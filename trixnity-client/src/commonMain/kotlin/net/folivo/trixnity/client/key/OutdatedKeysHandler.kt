package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import korlibs.io.async.async
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.client.utils.RetryLoopFlowState.PAUSE
import net.folivo.trixnity.client.utils.RetryLoopFlowState.RUN
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
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.crypto.sign.verify
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

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
    private val syncProcessingRunning = MutableStateFlow(false)
    private val normalLoopRunning = MutableStateFlow(false)

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.DEVICE_LISTS) {
            handleDeviceLists(it.syncResponse.deviceLists, api.sync.currentSyncState.value)
        }
            .unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList {
            updateDeviceKeysFromChangedMembership(it, isLoadingMembers = false, api.sync.currentSyncState.value)
        }
            .unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList { updateDeviceKeysFromChangedEncryption(it, api.sync.currentSyncState.value) }
            .unsubscribeOnCompletion(scope)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { updateLoop() }
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<ClientEvent.RoomEvent.StateEvent<MemberEventContent>>) {
        updateDeviceKeysFromChangedMembership(memberEvents, isLoadingMembers = true, api.sync.currentSyncState.value)
    }

    internal suspend fun handleDeviceLists(deviceList: Sync.Response.DeviceLists?, syncState: SyncState) =
        withContext(KeyStore.SkipOutdatedKeys) {
            // We want to load keys lazily. We don't have any e2e sessions in the initial sync, so we can skip it.
            if (syncState != SyncState.INITIAL_SYNC) {
                val startTrackingKeys = deviceList?.changed?.filter { keyStore.isTracked(it) }?.toSet().orEmpty()
                val stopTrackingKeys = deviceList?.left.orEmpty()

                trackKeys(
                    start = startTrackingKeys,
                    stop = stopTrackingKeys,
                    reason = "device list"
                )
            } else if (deviceList?.changed?.contains(userInfo.userId) == true) {
                trackKeys(
                    start = setOf(userInfo.userId),
                    stop = setOf(),
                    reason = "device list"
                )
            }
        }

    internal suspend fun updateDeviceKeysFromChangedMembership(
        events: List<ClientEvent.RoomEvent.StateEvent<MemberEventContent>>,
        isLoadingMembers: Boolean,
        syncState: SyncState,
    ) = withContext(KeyStore.SkipOutdatedKeys) {
        // We want to load keys lazily. We don't have any e2e sessions in the initial sync, so we can skip it.
        if (syncState != SyncState.INITIAL_SYNC) {
            val startTrackingKeys = mutableSetOf<UserId>()
            val stopTrackingKeys = mutableSetOf<UserId>()
            val joinedEncryptedRooms = async(start = CoroutineStart.LAZY) { roomStore.encryptedJoinedRooms() }

            events.forEach { event ->
                val room = roomStore.get(event.roomId).first()
                if (room?.encrypted == true) {
                    log.trace { "update keys from changed membership (event=$event)" }
                    val userId = UserId(event.stateKey)
                    val allowedMemberships =
                        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(event.roomId)
                            .first()?.content?.historyVisibility
                            .membershipsAllowedToReceiveKey
                    if (allowedMemberships.contains(event.content.membership)) {
                        if ((isLoadingMembers || room.membersLoaded) && !keyStore.isTracked(userId)) {
                            startTrackingKeys.add(userId)
                        }
                    } else {
                        if (keyStore.isTracked(userId)) {
                            val isActiveMemberOfAnyOtherEncryptedRoom =
                                roomStateStore.getByRooms<MemberEventContent>(joinedEncryptedRooms.await(), userId.full)
                                    .any {
                                        val membership = it.content.membership
                                        membership == Membership.JOIN || membership == Membership.INVITE
                                    }
                            if (!isActiveMemberOfAnyOtherEncryptedRoom) {
                                stopTrackingKeys.add(userId)
                            }
                        }
                    }
                }
            }
            trackKeys(
                start = startTrackingKeys,
                stop = stopTrackingKeys,
                reason = "member event"
            )
            joinedEncryptedRooms.cancelAndJoin()
        }
    }

    internal suspend fun updateDeviceKeysFromChangedEncryption(
        events: List<ClientEvent.RoomEvent.StateEvent<EncryptionEventContent>>,
        syncState: SyncState
    ) = withContext(KeyStore.SkipOutdatedKeys) {
        // We want to load keys lazily. We don't have any e2e sessions in the initial sync, so we can skip it.
        if (syncState != SyncState.INITIAL_SYNC) {
            log.trace { "update keys from changed encryption" }
            val startTrackingKeys = events.flatMap { event ->
                if (roomStore.get(event.roomId).first()?.membersLoaded == true) {
                    val allowedMemberships =
                        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(event.roomId)
                            .first()?.content?.historyVisibility
                            .membershipsAllowedToReceiveKey
                    roomStateStore.members(event.roomId, allowedMemberships)
                } else emptySet()
            }.toSet()
                .filterNot { keyStore.isTracked(it) }.toSet()
            trackKeys(
                start = startTrackingKeys,
                stop = emptySet(),
                reason = "encryption event"
            )
        }
    }

    //TODO we should also listen to HistoryVisibilityEventContent changes, which becomes important for MSC3061

    private suspend fun trackKeys(start: Set<UserId>, stop: Set<UserId>, reason: String) {
        if (start.isNotEmpty() || stop.isNotEmpty()) {
            tm.writeTransaction {
                log.debug { "change tracking keys because of $reason (start=$start stop=$stop)" }
                keyStore.updateOutdatedKeys { it + start - stop }
                stop.forEach { userId ->
                    keyStore.deleteDeviceKeys(userId)
                    keyStore.deleteCrossSigningKeys(userId)
                }
            }
        }
    }

    private val loopSyncStates = setOf(SyncState.STARTED, SyncState.INITIAL_SYNC, SyncState.RUNNING)
    internal suspend fun updateLoop() {
        val requestedState =
            combine(
                currentSyncState,
                syncProcessingRunning,
                keyStore.getOutdatedKeysFlow()
            ) { currentSyncState, syncProcessingRunning, outdatedKeys ->
                syncProcessingRunning.not()
                        && loopSyncStates.any { it == currentSyncState }
                        && outdatedKeys.isNotEmpty()
            }.map { if (it) RUN else PAUSE }
        retryLoop(
            requestedState = requestedState,
            scheduleLimit = 30.seconds,
            onError = { log.warn(it) { "failed update outdated keys" } },
            onCancel = { log.info { "stop update outdated keys, because job was cancelled" } },
        ) {
            log.debug { "update outdated keys in normal update loop" }
            normalLoopRunning.value = true
            updateOutdatedKeys()
            normalLoopRunning.value = false
        }
    }

    internal suspend fun updateOutdatedKeys() = coroutineScope {
        val userIds = keyStore.getOutdatedKeys()
        if (userIds.isEmpty()) return@coroutineScope
        log.debug { "try update outdated keys of $userIds" }
        val keysResponse = api.key.getKeys(
            deviceKeys = userIds.associateWith { emptySet() },
        ).getOrThrow()

        val joinedEncryptedRooms = async(start = CoroutineStart.LAZY) { roomStore.encryptedJoinedRooms() }
        val membershipsAllowedToReceiveKey = async(start = CoroutineStart.LAZY) {
            val historyVisibilities =
                roomStateStore.getByRooms<HistoryVisibilityEventContent>(joinedEncryptedRooms.await())
                    .mapNotNull { event ->
                        event.roomIdOrNull?.let { it to event.content.historyVisibility }
                    }
                    .toMap()
            joinedEncryptedRooms.await().associateWith { historyVisibilities[it].membershipsAllowedToReceiveKey }
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
                                getJoinedEncryptedRooms = joinedEncryptedRooms,
                                getMembershipsAllowedToReceiveKey = membershipsAllowedToReceiveKey
                            )
                        }
                        // indicate, that we fetched the keys of the user
                        keyStore.updateCrossSigningKeys(userId) { it ?: setOf() }
                        keyStore.updateDeviceKeys(userId) { it ?: mapOf() }

                        log.debug { "updated outdated keys of $userId" }
                        keyStore.updateOutdatedKeys { it - userId }
                    }
                }
            }
            yield()
        }
        joinedEncryptedRooms.cancelAndJoin()
        membershipsAllowedToReceiveKey.cancelAndJoin()
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
        getJoinedEncryptedRooms: Deferred<Set<RoomId>>,
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
                getJoinedEncryptedRooms.await()
                    .also {
                        if (it.isNotEmpty()) log.debug { "reset megolm sessions in rooms $it because of removed devices $removedDevices from $userId" }
                    }.forEach { roomId ->
                        olmCryptoStore.updateOutboundMegolmSession(roomId) { null }
                    }
            }

            addedDevices.isNotEmpty() -> {
                val joinedEncryptedRooms = getJoinedEncryptedRooms.await()
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