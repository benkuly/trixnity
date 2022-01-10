package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.model.sync.DeviceOneTimeKeysCount
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

private val log = KotlinLogging.logger {}

class OlmService(
    private val olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val api: MatrixApiClient,
    val json: Json,
) {
    private val account: OlmAccount =
        store.olm.account.value?.let { OlmAccount.unpickle(olmPickleKey, it) }
            ?: OlmAccount.create().also { store.olm.account.value = it.pickle(olmPickleKey) }
    private val utility = OlmUtility.create()

    fun free() {
        account.free()
        utility.free()
    }

    suspend fun getSelfSignedDeviceKeys() = sign.sign(
        DeviceKeys(
            userId = ownUserId,
            deviceId = ownDeviceId,
            algorithms = setOf(Olm, Megolm),
            keys = Keys(keysOf(ownEd25519Key, ownCurve25519Key))
        )
    )

    private val ownEd25519Key = Ed25519Key(ownDeviceId, account.identityKeys.ed25519)
    private val ownCurve25519Key = Curve25519Key(ownDeviceId, account.identityKeys.curve25519)

    val sign = OlmSignService(
        ownUserId = ownUserId,
        ownDeviceId = ownDeviceId,
        json = json,
        store = store,
        account = account,
        utility = utility,
    )
    val events = OlmEventService(
        olmPickleKey = olmPickleKey,
        ownUserId = ownUserId,
        ownDeviceId = ownDeviceId,
        json = json,
        account = account,
        store = store,
        api = api,
        signService = sign,
    )

    data class DecryptedOlmEvent(val encrypted: Event<OlmEncryptedEventContent>, val decrypted: OlmEvent<*>)

    private val _decryptedOlmEvents = MutableSharedFlow<DecryptedOlmEvent>()
    internal val decryptedOlmEvents = _decryptedOlmEvents.asSharedFlow()

    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse { syncResponse ->
            syncResponse.deviceOneTimeKeysCount?.also { handleDeviceOneTimeKeysCount(it) }
        }
        api.sync.subscribe(::handleMemberEvents)
        api.sync.subscribe(::handleEncryptionEvents)
        api.sync.subscribe(::handleOlmEncryptedToDeviceEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) { decryptedOlmEvents.collect(::handleOlmEncryptedRoomKeyEventContent) }
    }

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount) {
        val generateOneTimeKeysCount =
            (account.maxNumberOfOneTimeKeys / 2 - (count[KeyAlgorithm.SignedCurve25519] ?: 0))
                .coerceAtLeast(0)
        if (generateOneTimeKeysCount > 0) {
            account.generateOneTimeKeys(generateOneTimeKeysCount + account.maxNumberOfOneTimeKeys / 4)
            val signedOneTimeKeys = Keys(account.oneTimeKeys.curve25519.map {
                sign.signCurve25519Key(Key.Curve25519Key(keyId = it.key, value = it.value))
            }.toSet())
            log.debug { "generate and upload $generateOneTimeKeysCount one time keys: $signedOneTimeKeys" }
            api.keys.setDeviceKeys(oneTimeKeys = signedOneTimeKeys)
            account.markKeysAsPublished()
            store.olm.storeAccount(account, olmPickleKey)
        }
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
                pickleKey = olmPickleKey
            )
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
                        } == null) store.keys.updateDeviceKeys(UserId(event.stateKey)) { null }
                }
                JOIN, INVITE -> {
                    if (event.unsigned?.previousContent?.membership != event.content.membership
                        && !store.keys.isTracked(UserId(event.stateKey))
                    ) store.keys.outdatedKeys.update { it + UserId(event.stateKey) }
                }
                else -> {
                }
            }
        }
    }

    internal suspend fun handleEncryptionEvents(event: Event<EncryptionEventContent>) {
        if (event is StateEvent) {
            val outdatedKeys = store.roomState.members(event.roomId, JOIN, INVITE).filterNot {
                store.keys.isTracked(it)
            }
            store.keys.outdatedKeys.update { it + outdatedKeys }
        }
    }
}