package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.client.utils.retryLoop
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.crypto.key.get
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.crypto.sign.verify

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.OutdatedKeysHandler")

class OutdatedKeysHandler(
    private val api: MatrixClientServerApiClient,
    private val olmCryptoStore: OlmCryptoStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
    private val signService: SignService,
    private val keyTrustService: KeyTrustService,
    private val currentSyncState: CurrentSyncState,
    private val userInfo: UserInfo,
    private val tm: TransactionManager,
) : EventHandler, LazyMemberEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.DEVICE_LISTS) {
            handleDeviceLists(it.syncResponse.deviceLists, api.sync.currentSyncState.value)
        }.unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList {
            updateDeviceKeysFromChangedMembership(it, api.sync.currentSyncState.value)
        }.unsubscribeOnCompletion(scope)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { updateLoop() }
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<ClientEvent.RoomEvent.StateEvent<MemberEventContent>>) {
        updateDeviceKeysFromChangedMembership(memberEvents, api.sync.currentSyncState.value)
    }

    internal suspend fun handleDeviceLists(deviceList: Sync.Response.DeviceLists?, syncState: SyncState) =
        withContext(KeyStore.SkipOutdatedKeys) {
            log.debug { "handle new device list" }
            // We want to load keys lazily. We don't have any e2e sessions in the initial sync, so we can skip it.
            val trackOwnKey = deviceList?.changed?.contains(userInfo.userId) == true
            if (syncState != SyncState.INITIAL_SYNC) {
                val startTrackingKeys = deviceList?.changed?.filter { keyStore.isTracked(it) }?.toSet().orEmpty()
                    .let { if (trackOwnKey) it + userInfo.userId else it } // always track own key
                val stopTrackingKeys = deviceList?.left.orEmpty() - userInfo.userId // always track own key
                updateKeyTracking(
                    startTracking = startTrackingKeys,
                    stopTracking = stopTrackingKeys,
                    reason = "device list",
                )
            } else if (trackOwnKey) {
                updateKeyTracking(
                    startTracking = setOf(userInfo.userId),
                    stopTracking = setOf(),
                    reason = "device list",
                )
            }
        }

    internal suspend fun updateDeviceKeysFromChangedMembership(
        events: List<ClientEvent.RoomEvent.StateEvent<MemberEventContent>>,
        syncState: SyncState,
    ) = withContext(KeyStore.SkipOutdatedKeys) {
        // We want to load keys lazily. We don't have any e2e sessions in the initial sync, so we can skip it.
        if (syncState != SyncState.INITIAL_SYNC) {
            val stopTrackingKeys = mutableSetOf<UserId>()
            val encryptedRooms by lazy { async { roomStore.encryptedRooms() } }
            events.forEach { event ->
                roomStore.get(event.roomId).first()?.let { room ->
                    if (room.encrypted) {
                        log.trace { "update keys from changed membership (event=$event)" }
                        val userId = UserId(event.stateKey)
                        if (userId != userInfo.userId && keyStore.isTracked(userId)) {
                            val isActiveMemberOfAnyOtherEncryptedRoom =
                                roomStateStore.getByRooms<MemberEventContent>(
                                    encryptedRooms.await(),
                                    userId.full,
                                ).any { event ->
                                    val membership = event.content.membership
                                    membership == Membership.JOIN || membership == Membership.INVITE
                                }
                            if (!isActiveMemberOfAnyOtherEncryptedRoom) {
                                stopTrackingKeys.add(userId)
                            }
                        }
                    }
                }
            }
            updateKeyTracking(
                startTracking = setOf(),
                stopTracking = stopTrackingKeys,
                reason = "member event",
            )
        }
    }

    private suspend fun updateKeyTracking(startTracking: Set<UserId>, stopTracking: Set<UserId>, reason: String) {
        if (startTracking.isNotEmpty() || stopTracking.isNotEmpty()) {
            tm.writeTransaction {
                log.debug { "change tracking keys because of $reason (start=$startTracking stop=$stopTracking)" }
                keyStore.updateOutdatedKeys { it + startTracking - stopTracking }
                stopTracking.forEach { userId ->
                    keyStore.deleteDeviceKeys(userId)
                    keyStore.deleteCrossSigningKeys(userId)
                }
            }
        }
    }

    private suspend fun updateLoop() {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed update outdated keys, try again in $delay" } },
        ) {
            log.debug { "update outdated keys" }
            keyStore.getOutdatedKeysFlow().first { it.isNotEmpty() }
            updateOutdatedKeys()
        }
    }

    internal suspend fun updateOutdatedKeys() = withContext(KeyStore.SkipOutdatedKeys) {
        val userIds = keyStore.getOutdatedKeys()
        if (userIds.isEmpty()) return@withContext
        log.debug { "try update outdated keys of $userIds" }
        val keysResponse = api.key.getKeys(
            deviceKeys = userIds.associateWith { emptySet() },
        ).getOrThrow()

        val encryptedRooms by lazy { async { roomStore.encryptedRooms() } }
        val membershipsAllowedToReceiveKey by lazy {
            async {
                val historyVisibilities =
                    roomStateStore.getByRooms<HistoryVisibilityEventContent>(encryptedRooms.await())
                        .mapNotNull { event ->
                            event.roomIdOrNull?.let { it to event.content.historyVisibility }
                        }
                        .toMap()
                encryptedRooms.await()
                    .associateWith { historyVisibilities[it].membershipsAllowedToReceiveKey }
            }
        }

        userIds.chunked(25).forEach { userIdChunk ->
            tm.writeTransaction {
                userIdChunk.forEach { userId ->
                    launch {
                        keysResponse.masterKeys?.get(userId)?.let { masterKey ->
                            handleOutdatedCrossSigningKey(
                                userId = userId,
                                crossSigningKey = masterKey,
                                usage = CrossSigningKeysUsage.MasterKey,
                                signingKeyForVerification = masterKey.getSelfSigningKey(),
                                signingOptional = true
                            )
                        }
                        keysResponse.selfSigningKeys?.get(userId)?.let { selfSigningKey ->
                            handleOutdatedCrossSigningKey(
                                userId = userId,
                                crossSigningKey = selfSigningKey,
                                usage = CrossSigningKeysUsage.SelfSigningKey,
                                signingKeyForVerification = keyStore.getCrossSigningKey(
                                    userId,
                                    CrossSigningKeysUsage.MasterKey
                                )?.value?.signed?.get()
                            )
                        }
                        keysResponse.userSigningKeys?.get(userId)?.let { userSigningKey ->
                            handleOutdatedCrossSigningKey(
                                userId = userId,
                                crossSigningKey = userSigningKey,
                                usage = CrossSigningKeysUsage.UserSigningKey,
                                signingKeyForVerification = keyStore.getCrossSigningKey(
                                    userId,
                                    CrossSigningKeysUsage.MasterKey
                                )?.value?.signed?.get()
                            )
                        }
                        keysResponse.deviceKeys?.get(userId)?.let { devices ->
                            handleOutdatedDeviceKeys(
                                userId = userId,
                                devices = devices,
                                encryptedRooms = encryptedRooms,
                                getMembershipsAllowedToReceiveKey = membershipsAllowedToReceiveKey
                            )
                        }
                        // indicate, that we fetched the keys of the user
                        keyStore.updateCrossSigningKeys(userId) { it ?: setOf() }
                        keyStore.updateDeviceKeys(userId) { it ?: mapOf() }

                        if (userId != userInfo.userId
                            || keysResponse.deviceKeys?.get(userId)
                                ?.any { it.value.signed.deviceId == userInfo.deviceId } == true
                        ) {
                            log.debug { "updated outdated keys of $userId" }
                            keyStore.updateOutdatedKeys { it - userId }
                        } else {
                            throw IllegalStateException("updated outdated keys did not contain our own device")
                        }
                    }
                }
            }
            yield()
        }
        log.debug { "finished update outdated keys of $userIds" }
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
        encryptedRooms: Deferred<Set<RoomId>>,
        getMembershipsAllowedToReceiveKey: Deferred<Map<RoomId, Set<Membership>>>
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
        // we can do this, because an outbound megolm session does only exist, when loadMembers has been called
        when {
            removedDevices.isNotEmpty() -> {
                encryptedRooms.await()
                    .also {
                        if (it.isNotEmpty()) log.debug { "reset megolm sessions in rooms $it because of removed devices $removedDevices from $userId" }
                    }.forEach { roomId ->
                        olmCryptoStore.updateOutboundMegolmSession(roomId) { null }
                    }
            }

            addedDevices.isNotEmpty() -> {
                val joinedEncryptedRooms = encryptedRooms.await()
                if (joinedEncryptedRooms.isNotEmpty()) {
                    coroutineScope {
                        val memberships = async {
                            roomStateStore.getByRooms<MemberEventContent>(joinedEncryptedRooms, userId.full)
                                .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content.membership } }
                                .toMap()
                        }
                        val membershipsAllowedToReceiveKey = getMembershipsAllowedToReceiveKey.await()
                        memberships.await()
                            .filter { (roomId, membership) ->
                                checkNotNull(membershipsAllowedToReceiveKey[roomId]).contains(membership)
                            }
                            .keys
                            .also {
                                if (it.isNotEmpty()) log.debug { "notify megolm sessions in rooms $it about new devices $addedDevices from $userId" }
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
            }
        }
        keyStore.updateCrossSigningKeys(userId) { oldKeys ->
            val usersMasterKey = oldKeys?.find { it.value.signed.usage.contains(CrossSigningKeysUsage.MasterKey) }
            if (usersMasterKey != null) {
                val notFullyCrossSigned =
                    newDevices.any { it.value.trustLevel == KeySignatureTrustLevel.NotCrossSigned }
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
        keyStore.saveDeviceKeys(userId, newDevices)
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

    private suspend fun KeyStore.isTracked(userId: UserId): Boolean =
        getDeviceKeys(userId).first() != null
}