package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.sync.DeviceOneTimeKeysCount
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.room.RoomManager
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(FlowPreview::class)
class OlmManager(
    private val store: Store,
    private val api: MatrixApiClient,
    roomManager: RoomManager,
    val json: Json,
    loggerFactory: LoggerFactory
) {

    private val log = newLogger(loggerFactory)

    private val pickleKey = store.olm.pickleKey
    private val account: OlmAccount =
        store.olm.account.value?.let { OlmAccount.unpickle(pickleKey, it) }
            ?: OlmAccount.create().also { store.olm.account.value = it.pickle(pickleKey) }
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
        api = api,
        signService = sign,
        roomManager = roomManager,
        loggerFactory = loggerFactory
    )

    internal suspend fun startEventHandling() = coroutineScope {
        launch {
            api.sync.syncResponses.collect { syncResponse ->
                syncResponse.deviceOneTimeKeysCount?.also { handleDeviceOneTimeKeysCount(it) }
                syncResponse.deviceLists?.also { handleDeviceLists(it) }
            }
        }
        launch { store.deviceKeys.outdatedKeys.debounce(200).collectLatest(::handleOutdatedKeys) }
        launch { api.sync.events<OlmEncryptedEventContent>().collect(::handleOlmEncryptedToDeviceEvents) }
        launch { api.sync.events<MemberEventContent>().collect(::handleMemberEvents) }
        launch { api.sync.events<EncryptionEventContent>().collect(::handleEncryptionEvents) }
    }

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount) {
        val generateOneTimeKeysCount =
            (account.maxNumberOfOneTimeKeys / 2 - (count[KeyAlgorithm.SignedCurve25519] ?: 0))
                .coerceAtLeast(0)
        if (generateOneTimeKeysCount > 0) {
            log.debug { "generate and upload one time keys, because one time keys count is $generateOneTimeKeysCount" }
            account.generateOneTimeKeys(generateOneTimeKeysCount + account.maxNumberOfOneTimeKeys / 4)
            api.keys.uploadKeys(
                oneTimeKeys = Keys(account.oneTimeKeys.curve25519.map {
                    sign.signCurve25519Key(Curve25519Key(keyId = it.key, value = it.value))
                }.toSet())
            )
            account.markOneTimeKeysAsPublished()
            store.olm.storeAccount(account)
        }
    }

    internal suspend fun handleDeviceLists(deviceList: SyncResponse.DeviceLists) {
        log.debug { "set outdated device keys or remove old device keys" }
        deviceList.changed?.let { userIds ->
            store.deviceKeys.outdatedKeys.update { oldUserIds ->
                oldUserIds + userIds.filterNot { store.deviceKeys.isTracked(it) }
            }
        }
        deviceList.left?.forEach { userId ->
            store.deviceKeys.outdatedKeys.update { it - userId }
            store.deviceKeys.byUserId(userId).value = null
        }
    }

    internal suspend fun handleOutdatedKeys(userIds: Set<UserId>) = coroutineScope {
        if (userIds.isNotEmpty()) {
            log.debug { "update outdated device keys" }
            val joinedEncryptedRooms = async { store.rooms.encryptedJoinedRooms() }
            api.keys.getKeys(
                deviceKeys = userIds.associateWith { emptySet() },
                token = store.account.syncBatchToken.value
            ).deviceKeys.forEach { (userId, devices) ->
                log.debug { "update outdated device keys for user $userId" }
                val keys = devices.filter { (deviceId, deviceKeys) ->
                    // this prevents attacks from a malicious or compromised homeserver
                    userId == deviceKeys.signed.userId && deviceId == deviceKeys.signed.deviceId
                            && sign.verify(deviceKeys) == KeyVerifyState.Valid
                }.map { it.key to it.value.signed }.toMap()
                store.deviceKeys.byUserId(userId).update { oldDevices ->
                    // we must check that the Ed25519 key hasn't changed and otherwise ignore device (use old value)
                    val newDevices = keys + (oldDevices?.filter { (deviceId, deviceKeys) ->
                        val newEd25519 = keys[deviceId]?.get<Ed25519Key>() ?: return@filter false
                        val oldEd25519 = deviceKeys.get<Ed25519Key>()
                        oldEd25519 != newEd25519
                    } ?: emptyMap())
                    val diff = newDevices.filterNot { oldDevices?.get(it.key) == it.value }
                    if (diff.isNotEmpty()) {
                        log.debug { "look for encrypted room, where the user participates and notify megolm sessions about new device keys" }
                        joinedEncryptedRooms.await().filter { room ->
                            store.rooms.state.byId<MemberEventContent>(room.roomId, userId.full).value
                                ?.content?.membership.let { it == JOIN || it == INVITE }
                        }.forEach { room ->
                            store.olm.outboundMegolmSession(room.roomId).update { oms ->
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

    internal suspend fun handleOlmEncryptedToDeviceEvents(event: Event<OlmEncryptedEventContent>) {
        if (event is ToDeviceEvent) {
            val content = try {
                events.decryptOlm(event.content, event.sender).content
            } catch (e: Exception) {
                log.error(e) { "could not decrypt ${ToDeviceEvent::class.simpleName}" }
            }
            when (content) {
                is DummyEventContent -> {
                }
                is RoomKeyEventContent -> {
                    log.debug { "got inbound megolm session for room ${content.roomId}" }
                    store.olm.storeInboundMegolmSession(
                        roomId = content.roomId,
                        senderKey = event.content.senderKey,
                        sessionId = content.sessionId,
                        sessionKey = content.sessionKey
                    )
                }
            }
        }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) {
        if (event is StateEvent
            && store.rooms.byId(event.roomId).value?.encryptionAlgorithm == Megolm
        ) {
            log.debug { "handle membership change in an encrypted room" }
            when (event.content.membership) {
                LEAVE, BAN -> {
                    store.olm.outboundMegolmSession(event.roomId).value = null
                    if (store.rooms.encryptedJoinedRooms().find { room ->
                            store.rooms.state.byId<MemberEventContent>(room.roomId, event.stateKey).value
                                ?.content?.membership.let { it == JOIN || it == INVITE }
                        } == null) store.deviceKeys.byUserId(UserId(event.stateKey)).value = null
                }
                JOIN, INVITE -> {
                    if (event.previousContent?.membership != event.content.membership
                        && store.deviceKeys.isTracked(UserId(event.stateKey))
                    ) store.deviceKeys.outdatedKeys.update { it + UserId(event.stateKey) }
                }
                else -> {
                }
            }
        }
    }

    internal suspend fun handleEncryptionEvents(event: Event<EncryptionEventContent>) {
        if (event is StateEvent) {
            val outdatedKeys = store.rooms.state.members(event.roomId, JOIN, INVITE).filter {
                store.deviceKeys.isTracked(it)
            }
            store.deviceKeys.outdatedKeys.update { it + outdatedKeys }
        }
    }
}