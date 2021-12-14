package net.folivo.trixnity.client.key

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class KeyService(
    private val store: Store,
    private val olmSignService: OlmSignService,
    private val api: MatrixApiClient,
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    @OptIn(FlowPreview::class)
    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse { syncResponse ->
            syncResponse.deviceLists?.also { handleDeviceLists(it) }
        }
        scope.launch { store.keys.outdatedKeys.debounce(200).collectLatest(::handleOutdatedKeys) }
    }

    internal suspend fun handleDeviceLists(deviceList: SyncResponse.DeviceLists) {
        log.debug { "set outdated device keys or remove old device keys" }
        deviceList.changed?.let { userIds ->
            store.keys.outdatedKeys.update { oldUserIds ->
                oldUserIds + userIds.filter { store.keys.isTracked(it) }
            }
        }
        deviceList.left?.forEach { userId ->
            store.keys.outdatedKeys.update { it - userId }
            store.keys.updateDeviceKeys(userId) { null }
            store.keys.updateCrossSigningKeys(userId) { null }
        }
    }

    internal suspend fun handleOutdatedKeys(userIds: Set<UserId>) = coroutineScope {
        if (userIds.isNotEmpty()) {
            log.debug { "update outdated device keys" }
            val joinedEncryptedRooms = async(start = CoroutineStart.LAZY) { store.room.encryptedJoinedRooms() }
            val keysResponse = api.keys.getKeys(
                deviceKeys = userIds.associateWith { emptySet() },
                token = store.account.syncBatchToken.value
            )

            keysResponse.masterKeys?.forEach { (userId, masterKey) ->
                handleOutdatedMasterKey(userId, masterKey)
            }
            keysResponse.selfSigningKeys?.forEach { (userId, selfSigningKey) ->
                handleOutdatedSelfSigningKey(userId, selfSigningKey)
            }
            keysResponse.userSigningKeys?.forEach { (userId, userSigningKey) ->
                handleOutdatedUserSigningKey(userId, userSigningKey)
            }
            keysResponse.deviceKeys?.forEach { (userId, devices) ->
                handleOutdatedDeviceKeys(userId, devices, joinedEncryptedRooms)
            }
            joinedEncryptedRooms.cancel()
            store.keys.outdatedKeys.update { it - userIds }
        }
    }

    private suspend fun handleOutdatedMasterKey(
        userId: UserId,
        masterKey: Signed<CrossSigningKeys, UserId>
    ) {
        val oldMasterKey = store.keys.getCrossSigningKey(userId, MasterKey)
        when (val signatureVerification = olmSignService.verify(masterKey)) {
            VerifyResult.Valid, VerifyResult.MissingSignature -> {
                val oldMasterKeyWasVerified = when (val trustLevel = oldMasterKey?.trustLevel) {
                    is Valid -> trustLevel.verified
                    else -> false
                }
                val newMasterKeyTrustLevel =
                    if (oldMasterKey == null) calculateCrossSigningKeysTrustLevel(masterKey)
                    else MasterKeyChangedRecently(oldMasterKeyWasVerified)

                val newMasterKey = StoredCrossSigningKey(masterKey, newMasterKeyTrustLevel)
                store.keys.updateCrossSigningKeys(userId) {
                    if (oldMasterKey != null) (it ?: setOf()) + newMasterKey - oldMasterKey
                    else (it ?: setOf()) + newMasterKey
                }
            }
            else -> {
                log.warning { "Signatures from the master key of $userId were not valid: $signatureVerification!" }
            }
        }
    }

    private suspend fun handleOutdatedSelfSigningKey(
        userId: UserId,
        selfSigningKey: Signed<CrossSigningKeys, UserId>
    ) {
        val signatureVerification = olmSignService.verify(selfSigningKey)
        if (signatureVerification == VerifyResult.Valid) {
            val newSelfSigningKey =
                StoredCrossSigningKey(selfSigningKey, calculateCrossSigningKeysTrustLevel(selfSigningKey))
            store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                ((oldKeys?.filterNot { it.value.signed.usage.contains(CrossSigningKeysUsage.SelfSigningKey) }
                    ?.toSet() ?: setOf())
                        + newSelfSigningKey)
            }
        } else {
            log.warning { "Signatures from the self signing key of $userId were not valid: $signatureVerification!" }
        }
    }

    private suspend fun handleOutdatedUserSigningKey(
        userId: UserId,
        userSigningKey: Signed<CrossSigningKeys, UserId>
    ) {
        val signatureVerification = olmSignService.verify(userSigningKey)
        if (signatureVerification == VerifyResult.Valid) {
            val newUserSigningKey =
                StoredCrossSigningKey(userSigningKey, calculateCrossSigningKeysTrustLevel(userSigningKey))
            store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                ((oldKeys?.filterNot { it.value.signed.usage.contains(CrossSigningKeysUsage.UserSigningKey) }
                    ?.toSet() ?: setOf())
                        + newUserSigningKey)
            }
        } else {
            log.warning { "Signatures from the user signing key of $userId were not valid: $signatureVerification!" }
        }
    }

    private suspend fun handleOutdatedDeviceKeys(
        userId: UserId,
        devices: Map<String, Signed<DeviceKeys, UserId>>,
        joinedEncryptedRooms: Deferred<List<RoomId>>
    ) {
        log.debug { "update received outdated device keys for user $userId" }
        val oldDevices = store.keys.getDeviceKeys(userId)
        val newDevices = devices.filter { (deviceId, deviceKeys) ->
            val signatureVerification = olmSignService.verifySelfSignedDeviceKeys(deviceKeys)
            (userId == deviceKeys.signed.userId && deviceId == deviceKeys.signed.deviceId
                    && signatureVerification == VerifyResult.Valid)
                .also {
                    if (!it) log.warning {
                        "ignore device keys from $userId ($deviceId) with signature verification " +
                                "result $signatureVerification. This prevents attacks from a malicious or compromised homeserver."
                    }
                }
        }.mapValues { (_, deviceKeys) ->
            StoredDeviceKeys(deviceKeys, calculateDeviceKeysTrustLevel(deviceKeys))
        }
        val addedDeviceKeys = if (oldDevices != null) newDevices.keys - oldDevices.keys else newDevices.keys
        if (addedDeviceKeys.isNotEmpty()) {
            log.debug { "look for encrypted room, where the user participates and notify megolm sessions about new device keys" }
            joinedEncryptedRooms.await()
                .filter { roomId ->
                    store.roomState.getByStateKey<MemberEventContent>(roomId, userId.full)
                        ?.content?.membership.let { it == MemberEventContent.Membership.JOIN || it == MemberEventContent.Membership.INVITE }
                }.forEach { roomId ->
                    store.olm.updateOutboundMegolmSession(roomId) { oms ->
                        oms?.copy(
                            newDevices = oms.newDevices + Pair(
                                userId,
                                oms.newDevices[userId]?.plus(addedDeviceKeys) ?: addedDeviceKeys
                            )
                        )
                    }
                }
        }
        val usersMasterKey = store.keys.getCrossSigningKey(userId, MasterKey)
        if (usersMasterKey != null) {
            val notFullyCrossSigned = newDevices.any { it.value.trustLevel == NotCrossSigned }
            val oldMasterKeyTrustLevel = usersMasterKey.trustLevel
            val newMasterKeyTrustLevel = when (oldMasterKeyTrustLevel) {
                is Valid -> {
                    if (notFullyCrossSigned) {
                        log.debug { "mark master key as ${NotAllDeviceKeysCrossSigned::class.simpleName}" }
                        NotAllDeviceKeysCrossSigned(oldMasterKeyTrustLevel.verified)
                    } else oldMasterKeyTrustLevel
                }
                is CrossSigned -> {
                    if (notFullyCrossSigned) {
                        log.debug { "mark master key as ${NotAllDeviceKeysCrossSigned::class.simpleName}" }
                        NotAllDeviceKeysCrossSigned(oldMasterKeyTrustLevel.verified)
                    } else oldMasterKeyTrustLevel
                }
                is NotAllDeviceKeysCrossSigned -> {
                    if (notFullyCrossSigned) oldMasterKeyTrustLevel
                    else {
                        log.debug { "unmark master key as ${NotAllDeviceKeysCrossSigned::class.simpleName}" }
                        store.keys.updateCrossSigningKeys(userId) { it?.minus(usersMasterKey) }
                        handleOutdatedMasterKey(userId, usersMasterKey.value) // we let him update the trust level
                        null
                    }
                }
                else -> oldMasterKeyTrustLevel
            }
            if (newMasterKeyTrustLevel != null && oldMasterKeyTrustLevel != newMasterKeyTrustLevel) {
                store.keys.updateCrossSigningKeys(userId) {
                    if (it != null) it - usersMasterKey + usersMasterKey.copy(trustLevel = newMasterKeyTrustLevel)
                    else null
                }
            }
        }
        store.keys.updateDeviceKeys(userId) { newDevices }
    }

    internal suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        log.debug { "calculate trust level for ${deviceKeys.signed}" }
        val userId = deviceKeys.signed.userId
        val deviceId = deviceKeys.signed.deviceId
        return calculateTrustLevel(userId, deviceKeys.signatures, deviceKeys.getVerificationState(userId, deviceId))
    }

    internal suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        log.debug { "calculate trust level for ${crossSigningKeys.signed}" }
        val userId = crossSigningKeys.signed.userId
        return calculateTrustLevel(
            userId,
            crossSigningKeys.signatures,
            crossSigningKeys.getVerificationState(userId)
        )
    }

    private suspend fun calculateTrustLevel(
        userId: UserId,
        signatures: Signatures<UserId>,
        keyVerificationState: KeyVerificationState?
    ): KeySignatureTrustLevel {
        return when (keyVerificationState) {
            is KeyVerificationState.Verified -> Valid(true)
            is KeyVerificationState.Blocked -> Blocked
            else -> searchSignaturesForTrustLevel(signatures)
                ?: if (store.keys.getCrossSigningKey(userId, MasterKey) == null) Valid(false)
                else NotCrossSigned
        }
    }

    private suspend fun searchSignaturesForTrustLevel(
        signatures: Signatures<UserId>,
        visitedKeys: MutableSet<Key> = mutableSetOf()
    ): KeySignatureTrustLevel? {
        log.debug { "search in signatures for trust level: $signatures" }
        val states = signatures.flatMap { (userId, signatureKeys) ->
            signatureKeys.flatMap { signatureKey ->
                val crossSigningKey = signatureKey.keyId?.let { store.keys.getCrossSigningKey(userId, it) }?.value
                val crossSigningKeyState =
                    if (crossSigningKey?.signed?.keys?.keys?.none { visitedKeys.contains(it) } == true) {
                        crossSigningKey.signed.keys.keys.let { visitedKeys.addAll(it) }
                        when (crossSigningKey.getVerificationState(userId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> searchSignaturesForTrustLevel(crossSigningKey.signatures, visitedKeys)
                        }
                    } else null

                val signedByUsersMasterKeyState =
                    if (crossSigningKey?.signed?.usage?.contains(MasterKey) == true
                        && crossSigningKey.signed.userId == userId
                    ) CrossSigned(false)
                    else null

                val deviceKey = signatureKey.keyId?.let { store.keys.getDeviceKey(userId, it) }?.value
                val deviceKeyState =
                    if (deviceKey?.signed?.keys?.keys?.none { visitedKeys.contains(it) } == true) {
                        deviceKey.signed.keys.keys.let { visitedKeys.addAll(it) }
                        when (deviceKey.getVerificationState(userId, deviceKey.signed.deviceId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> searchSignaturesForTrustLevel(deviceKey.signatures, visitedKeys)
                        }
                    } else null

                listOf(crossSigningKeyState, deviceKeyState, signedByUsersMasterKeyState)
            }.toSet()
        }.toSet()
        return when {
            states.any { it is CrossSigned && it.verified } -> CrossSigned(true)
            states.any { it is CrossSigned && !it.verified } -> CrossSigned(false)
            states.contains(Blocked) -> Blocked
            else -> null
        }
    }

    private suspend fun Keys.getVerificationState(userId: UserId, deviceId: String? = null) =
        this.asFlow().mapNotNull { store.keys.getKeyVerificationState(it, userId, deviceId) }.firstOrNull()

    private suspend fun SignedCrossSigningKeys.getVerificationState(userId: UserId) =
        this.signed.keys.getVerificationState(userId)

    private suspend fun SignedDeviceKeys.getVerificationState(userId: UserId, deviceId: String) =
        this.signed.keys.getVerificationState(userId, deviceId)

    /**
     * @return the trust level of a user or null, if the timeline event is not a megolm encrypted event.
     */
    suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
        scope: CoroutineScope
    ): StateFlow<DeviceTrustLevel?>? {
        val event = timelineEvent.event
        val content = event.content
        return if (event is Event.MessageEvent && content is EncryptedEventContent.MegolmEncryptedEventContent) {
            store.keys.getDeviceKey(timelineEvent.event.sender, content.deviceId, scope)
                .map {
                    when (val trustLevel = it?.trustLevel) {
                        is Valid -> DeviceTrustLevel.Valid(trustLevel.verified)
                        is CrossSigned -> DeviceTrustLevel.CrossSigned(trustLevel.verified)
                        NotCrossSigned -> DeviceTrustLevel.NotCrossSigned
                        is MasterKeyChangedRecently -> null
                        is NotAllDeviceKeysCrossSigned -> null
                        Blocked -> DeviceTrustLevel.Blocked
                        null -> null
                    }
                }.stateIn(scope)
        } else null
    }

    /**
     * This should be called, when the user has been notified about a recently changed master key.
     */
    suspend fun unmarkMasterKeyChangedRecently(userId: UserId) {
        store.keys.updateCrossSigningKeys(userId) { keys ->
            val masterKey = keys?.firstOrNull { it.value.signed.usage.contains(MasterKey) }
            if (masterKey != null && masterKey.trustLevel is MasterKeyChangedRecently)
                keys - masterKey + masterKey.copy(trustLevel = Valid(false))
            else null
        }
    }

    /**
     * @return the trust level of a user. This will only be present, if the user has cross signing enabled.
     */
    suspend fun getTrustLevel(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<UserTrustLevel?> {
        return store.keys.getCrossSigningKey(userId, MasterKey, scope).map {
            when (val trustLevel = it?.trustLevel) {
                is Valid -> UserTrustLevel.CrossSigned(trustLevel.verified)
                is CrossSigned -> UserTrustLevel.CrossSigned(trustLevel.verified)
                NotCrossSigned -> null
                is MasterKeyChangedRecently -> UserTrustLevel.MasterKeyChangedRecently(trustLevel.previousMasterKeyWasVerified)
                is NotAllDeviceKeysCrossSigned -> UserTrustLevel.NotAllDevicesCrossSigned(trustLevel.verified)
                Blocked -> UserTrustLevel.Blocked
                null -> null
            }
        }.stateIn(scope)
    }
}