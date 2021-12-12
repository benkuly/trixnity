package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.sync.DeviceOneTimeKeysCount
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class OlmService(
    private val store: Store,
    private val secureStore: SecureStore,
    private val api: MatrixApiClient,
    val json: Json,
    loggerFactory: LoggerFactory
) {

    private val log = newLogger(loggerFactory)

    private val account: OlmAccount =
        store.olm.account.value?.let { OlmAccount.unpickle(secureStore.olmPickleKey, it) }
            ?: OlmAccount.create().also { store.olm.account.value = it.pickle(secureStore.olmPickleKey) }
    private val utility = OlmUtility.create()

    fun free() {
        account.free()
        utility.free()
    }

    private val myUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")
    private val myDeviceId = store.account.deviceId.value ?: throw IllegalArgumentException("deviceId must not be null")
    val myDeviceKeys: Signed<DeviceKeys, UserId>
        get() = sign.sign(
            DeviceKeys(
                userId = myUserId,
                deviceId = myDeviceId,
                algorithms = setOf(Olm, Megolm),
                keys = Keys(keysOf(myEd25519Key, myCurve25519Key))
            )
        )
    private val myEd25519Key = Ed25519Key(myDeviceId, account.identityKeys.ed25519)
    private val myCurve25519Key = Curve25519Key(myDeviceId, account.identityKeys.curve25519)

    val sign = OlmSignService(
        json = json,
        store = store,
        account = account,
        utility = utility
    )
    val events = OlmEventService(
        json = json,
        account = account,
        store = store,
        secureStore = secureStore,
        api = api,
        signService = sign,
        loggerFactory = loggerFactory
    )

    data class DecryptedOlmEvent(val encrypted: Event<OlmEncryptedEventContent>, val decrypted: OlmEvent<*>)

    private val _decryptedOlmEvents = MutableSharedFlow<DecryptedOlmEvent>()
    internal val decryptedOlmEvents = _decryptedOlmEvents.asSharedFlow()

    @OptIn(FlowPreview::class)
    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse { syncResponse ->
            syncResponse.deviceOneTimeKeysCount?.also { handleDeviceOneTimeKeysCount(it) }
            syncResponse.deviceLists?.also { handleDeviceLists(it) }
        }
        scope.launch { store.deviceKeys.outdatedKeys.debounce(200).collectLatest(::handleOutdatedKeys) }
        api.sync.subscribe(::handleMemberEvents)
        api.sync.subscribe(::handleEncryptionEvents)
        api.sync.subscribe(::handleOlmEncryptedToDeviceEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) { decryptedOlmEvents.collect(::handleOlmEncryptedRoomKeyEventContent) }
    }

    internal suspend fun handleOlmEncryptedToDeviceEvents(event: Event<OlmEncryptedEventContent>) {
        if (event is ToDeviceEvent) {
            val decryptedEvent = try {
                events.decryptOlm(event.content, event.sender)
            } catch (e: Exception) {
                log.error(e) { "could not decrypt ${ToDeviceEvent::class.simpleName}" }
                null
            }
            if (decryptedEvent != null) {
                _decryptedOlmEvents.emit(DecryptedOlmEvent(event, decryptedEvent))
            }
        }
    }

    internal suspend fun handleOlmEncryptedRoomKeyEventContent(event: DecryptedOlmEvent) {
        val content = event.decrypted.content
        if (content is RoomKeyEventContent) {
            log.debug { "got inbound megolm session for room ${content.roomId}" }
            store.olm.storeInboundMegolmSession(
                roomId = content.roomId,
                senderKey = event.encrypted.content.senderKey,
                sessionId = content.sessionId,
                sessionKey = content.sessionKey,
                pickleKey = secureStore.olmPickleKey
            )
        }
    }

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount) {
        val generateOneTimeKeysCount =
            (account.maxNumberOfOneTimeKeys / 2 - (count[KeyAlgorithm.SignedCurve25519] ?: 0))
                .coerceAtLeast(0)
        if (generateOneTimeKeysCount > 0) {
            account.generateOneTimeKeys(generateOneTimeKeysCount + account.maxNumberOfOneTimeKeys / 4)
            val signedOneTimeKeys = Keys(account.oneTimeKeys.curve25519.map {
                sign.signCurve25519Key(Curve25519Key(keyId = it.key, value = it.value))
            }.toSet())
            log.debug { "generate and upload $generateOneTimeKeysCount one time keys: $signedOneTimeKeys" }
            api.keys.uploadKeys(oneTimeKeys = signedOneTimeKeys)
            account.markKeysAsPublished()
            store.olm.storeAccount(account, secureStore.olmPickleKey)
        }
    }

    internal suspend fun handleDeviceLists(deviceList: SyncResponse.DeviceLists) {
        log.debug { "set outdated device keys or remove old device keys" }
        deviceList.changed?.let { userIds ->
            store.deviceKeys.outdatedKeys.update { oldUserIds ->
                oldUserIds + userIds.filter { store.deviceKeys.isTracked(it) }
            }
        }
        deviceList.left?.forEach { userId ->
            store.deviceKeys.outdatedKeys.update { it - userId }
            store.deviceKeys.update(userId) { null }
        }
    }

    internal suspend fun handleOutdatedKeys(userIds: Set<UserId>) = coroutineScope {
        if (userIds.isNotEmpty()) {
            log.debug { "update outdated device keys" }
            val joinedEncryptedRooms = async { store.room.encryptedJoinedRooms() }
            api.keys.getKeys(
                deviceKeys = userIds.associateWith { emptySet() },
                token = store.account.syncBatchToken.value
            ).deviceKeys.forEach { (userId, devices) ->
                log.debug { "update received outdated device keys for user $userId" }
                val keys = devices.filter { (deviceId, deviceKeys) ->
                    // this prevents attacks from a malicious or compromised homeserver
                    userId == deviceKeys.signed.userId && deviceId == deviceKeys.signed.deviceId
                            && sign.verify(deviceKeys) == KeyVerificationState.Valid
                }.map { it.key to it.value.signed }.toMap()
                store.deviceKeys.update(userId) { oldDevices ->
                    // we must check that the Ed25519 key hasn't changed and otherwise ignore device (use old value)
                    val newDevices = keys + (oldDevices?.filter { (deviceId, deviceKeys) ->
                        val newEd25519 = keys[deviceId]?.get<Ed25519Key>() ?: return@filter false
                        val oldEd25519 = deviceKeys.get<Ed25519Key>()
                        oldEd25519 != newEd25519
                    } ?: emptyMap())
                    val diff = newDevices.filterNot { oldDevices?.get(it.key) == it.value }
                    if (diff.isNotEmpty()) {
                        log.debug { "look for encrypted room, where the user participates and notify megolm sessions about new device keys" }
                        joinedEncryptedRooms.await()
                            .filter { roomId ->
                                store.roomState.getByStateKey<MemberEventContent>(roomId, userId.full)
                                    ?.content?.membership.let { it == JOIN || it == INVITE }
                            }.forEach { roomId ->
                                store.olm.updateOutboundMegolmSession(roomId) { oms ->
                                    oms?.copy(
                                        newDevices = oms.newDevices + Pair(
                                            userId,
                                            oms.newDevices[userId]?.plus(diff.keys) ?: diff.keys
                                        )
                                    )
                                }
                            }
                    }
                    newDevices
                }
            }
            store.deviceKeys.outdatedKeys.update { it - userIds }
        }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) {
        if (event is StateEvent && store.room.get(event.roomId).value?.encryptionAlgorithm == Megolm) {
            log.debug { "handle membership change in an encrypted room" }
            when (event.content.membership) {
                LEAVE, BAN -> {
                    store.olm.updateOutboundMegolmSession(event.roomId) { null }
                    if (store.room.encryptedJoinedRooms().find { roomId ->
                            store.roomState.getByStateKey<MemberEventContent>(roomId, event.stateKey)
                                ?.content?.membership.let { it == JOIN || it == INVITE }
                        } == null) store.deviceKeys.update(UserId(event.stateKey)) { null }
                }
                JOIN, INVITE -> {
                    if (event.unsigned?.previousContent?.membership != event.content.membership
                        && !store.deviceKeys.isTracked(UserId(event.stateKey))
                    ) store.deviceKeys.outdatedKeys.update { it + UserId(event.stateKey) }
                }
                else -> {
                }
            }
        }
    }

    internal suspend fun handleEncryptionEvents(event: Event<EncryptionEventContent>) {
        if (event is StateEvent) {
            val outdatedKeys = store.roomState.members(event.roomId, JOIN, INVITE).filterNot {
                store.deviceKeys.isTracked(it)
            }
            store.deviceKeys.outdatedKeys.update { it + outdatedKeys }
        }
    }
}