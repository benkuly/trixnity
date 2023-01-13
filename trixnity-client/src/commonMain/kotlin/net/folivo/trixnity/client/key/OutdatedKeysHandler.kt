package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.crypto.sign.verify
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class OutdatedKeysHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
    private val signService: SignService,
    private val keyTrustService: KeyTrustService,
    private val currentSyncState: CurrentSyncState,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleOutdatedKeys() }
    }

    internal suspend fun handleOutdatedKeys() {
        currentSyncState.retryInfiniteWhenSyncIs(
            SyncState.STARTED, SyncState.INITIAL_SYNC, SyncState.RUNNING,
            scheduleLimit = 30.seconds,
            onError = { log.warn(it) { "failed update outdated keys" } },
            onCancel = { log.info { "stop update outdated keys, because job was cancelled" } },
        ) {
            coroutineScope {
                keyStore.outdatedKeys.collect { userIds ->
                    if (userIds.isNotEmpty()) {
                        log.debug { "try update outdated keys of $userIds" }
                        val keysResponse = api.keys.getKeys(
                            deviceKeys = userIds.associateWith { emptySet() },
                            token = accountStore.syncBatchToken.value
                        ).getOrThrow()

                        val joinedEncryptedRooms = lazy { roomStore.encryptedJoinedRooms() }
                        userIds.forEach { userId ->
                            keysResponse.masterKeys?.get(userId)?.let { masterKey ->
                                handleOutdatedCrossSigningKey(
                                    userId,
                                    masterKey,
                                    CrossSigningKeysUsage.MasterKey,
                                    masterKey.getSelfSigningKey(),
                                    true
                                )
                            }
                            keysResponse.selfSigningKeys?.get(userId)?.let { selfSigningKey ->
                                handleOutdatedCrossSigningKey(
                                    userId, selfSigningKey, CrossSigningKeysUsage.SelfSigningKey,
                                    keyStore.getCrossSigningKey(
                                        userId,
                                        CrossSigningKeysUsage.MasterKey
                                    )?.value?.signed?.get()
                                )
                            }
                            keysResponse.userSigningKeys?.get(userId)?.let { userSigningKey ->
                                handleOutdatedCrossSigningKey(
                                    userId, userSigningKey, CrossSigningKeysUsage.UserSigningKey,
                                    keyStore.getCrossSigningKey(
                                        userId,
                                        CrossSigningKeysUsage.MasterKey
                                    )?.value?.signed?.get()
                                )
                            }
                            keysResponse.deviceKeys?.get(userId)?.let { devices ->
                                handleOutdatedDeviceKeys(userId, devices, joinedEncryptedRooms)
                            }
                            // indicate, that we fetched the keys of the user
                            keyStore.updateCrossSigningKeys(userId) { it ?: setOf() }
                            keyStore.updateDeviceKeys(userId) { it ?: mapOf() }

                            log.debug { "updated outdated keys of $userId" }
                            keyStore.outdatedKeys.update { it - userId }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleOutdatedCrossSigningKey(
        userId: UserId,
        crossSigningKey: Signed<CrossSigningKeys, UserId>,
        usage: CrossSigningKeysUsage,
        signingKeyForVerification: Key.Ed25519Key?,
        signingOptional: Boolean = false
    ) {
        val signatureVerification =
            signService.verify(crossSigningKey, mapOf(userId to setOfNotNull(signingKeyForVerification)))
        if (signatureVerification == VerifyResult.Valid
            || signingOptional && signatureVerification is VerifyResult.MissingSignature
        ) {
            val oldTrustLevel = keyStore.getCrossSigningKey(userId, usage)?.trustLevel
            val trustLevel = keyTrustService.calculateCrossSigningKeysTrustLevel(crossSigningKey)
            log.trace { "updated outdated cross signing ${usage.name} key of user $userId with trust level $trustLevel (was $oldTrustLevel)" }
            val newKey = StoredCrossSigningKeys(crossSigningKey, trustLevel)
            keyStore.updateCrossSigningKeys(userId) { oldKeys ->
                ((oldKeys?.filterNot { it.value.signed.usage.contains(usage) }
                    ?.toSet() ?: setOf())
                        + newKey)
            }
            if (oldTrustLevel != trustLevel) {
                newKey.value.signed.get<Key.Ed25519Key>()
                    ?.let { keyTrustService.updateTrustLevelOfKeyChainSignedBy(userId, it) }
            }
        } else {
            log.warn { "Signatures from cross signing key (${usage.name}) of $userId were not valid: $signatureVerification!" }
        }
    }

    private suspend fun handleOutdatedDeviceKeys(
        userId: UserId,
        devices: Map<String, SignedDeviceKeys>,
        joinedEncryptedRooms: Lazy<List<RoomId>>
    ) {
        val oldDevices = keyStore.getDeviceKeys(userId).first().orEmpty()
        val newDevices = devices.filter { (deviceId, deviceKeys) ->
            val signatureVerification =
                signService.verify(deviceKeys, mapOf(userId to setOfNotNull(deviceKeys.getSelfSigningKey())))
            (userId == deviceKeys.signed.userId && deviceId == deviceKeys.signed.deviceId
                    && signatureVerification == VerifyResult.Valid)
                .also {
                    if (!it) log.warn { "Signatures from device key $deviceId of $userId were not valid: $signatureVerification!" }
                }
        }.mapValues { (_, deviceKeys) ->
            val trustLevel = keyTrustService.calculateDeviceKeysTrustLevel(deviceKeys)
            log.trace { "updated outdated device keys ${deviceKeys.signed.deviceId} of user $userId with trust level $trustLevel" }
            StoredDeviceKeys(deviceKeys, trustLevel)
        }
        val addedDevices = newDevices.keys - oldDevices.keys
        val removedDevices = oldDevices.keys - newDevices.keys
        when {
            removedDevices.isNotEmpty() -> {
                joinedEncryptedRooms.value
                    .also {
                        if (it.isNotEmpty()) log.trace { "reset megolm sessions in rooms $it because of remove devices from $userId: $removedDevices" }
                    }.forEach { roomId ->
                        olmCryptoStore.updateOutboundMegolmSession(roomId) { null }
                    }
            }

            addedDevices.isNotEmpty() -> {
                joinedEncryptedRooms.value
                    .filter { roomId ->
                        val allowedMemberships =
                            roomStateStore.getByStateKey<HistoryVisibilityEventContent>(roomId).first()
                                ?.content?.historyVisibility.membershipsAllowedToReceiveKey
                        roomStateStore.getByStateKey<MemberEventContent>(roomId, userId.full).first()
                            ?.content?.membership.let { allowedMemberships.contains(it) }
                    }.also {
                        if (it.isNotEmpty()) log.trace { "notify megolm sessions in rooms $it about new device keys from $userId: $addedDevices" }
                    }.forEach { roomId ->
                        olmCryptoStore.updateOutboundMegolmSession(roomId) { oms ->
                            oms?.copy(
                                newDevices = oms.newDevices + Pair(
                                    userId,
                                    oms.newDevices[userId]?.plus(addedDevices) ?: addedDevices
                                )
                            )
                        }
                    }
            }
        }
        keyStore.updateCrossSigningKeys(userId) { oldKeys ->
            val usersMasterKey = oldKeys?.find { it.value.signed.usage.contains(CrossSigningKeysUsage.MasterKey) }
            if (usersMasterKey != null) {
                val notFullyCrossSigned =
                    newDevices.any { it.value.trustLevel == KeySignatureTrustLevel.NotCrossSigned() }
                val oldMasterKeyTrustLevel = usersMasterKey.trustLevel
                val newMasterKeyTrustLevel = when (oldMasterKeyTrustLevel) {
                    is KeySignatureTrustLevel.CrossSigned -> {
                        if (notFullyCrossSigned) {
                            log.trace { "mark master key of $userId as ${KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned::class.simpleName}" }
                            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(oldMasterKeyTrustLevel.verified)
                        } else oldMasterKeyTrustLevel
                    }

                    else -> oldMasterKeyTrustLevel
                }
                if (oldMasterKeyTrustLevel != newMasterKeyTrustLevel) {
                    (oldKeys - usersMasterKey) + usersMasterKey.copy(trustLevel = newMasterKeyTrustLevel)
                } else oldKeys
            } else oldKeys
        }
        keyStore.updateDeviceKeys(userId) { newDevices }
    }

    /**
     * Only DeviceKeys and CrossSigningKeys are supported.
     */
    private inline fun <reified T> Signed<T, UserId>.getSelfSigningKey(): Key.Ed25519Key? {
        return when (val signed = this.signed) {
            is DeviceKeys -> signed.keys.get()
            is CrossSigningKeys -> signed.keys.get()
            else -> null
        }
    }
}