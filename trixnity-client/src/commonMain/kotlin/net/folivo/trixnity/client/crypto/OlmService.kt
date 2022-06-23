package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.DeviceOneTimeKeysCount
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility

private val log = KotlinLogging.logger {}

interface IOlmService {
    val sign: IOlmSignService
    val event: IOlmEventService

    data class DecryptedOlmEventContainer(
        val encrypted: ClientEvent<OlmEncryptedEventContent>,
        val decrypted: DecryptedOlmEvent<*>
    )

    suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId>
}

class OlmService(
    private val olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val api: MatrixClientServerApiClient,
    json: Json,
    private val olmAccount: OlmAccount,
    olmUtility: OlmUtility,
    scope: CoroutineScope,
) : IOlmService {

    override suspend fun getSelfSignedDeviceKeys() = sign.sign(
        DeviceKeys(
            userId = ownUserId,
            deviceId = ownDeviceId,
            algorithms = setOf(Olm, Megolm),
            keys = Keys(keysOf(ownEd25519Key, ownCurve25519Key))
        )
    )

    private val ownEd25519Key = Ed25519Key(ownDeviceId, olmAccount.identityKeys.ed25519)
    private val ownCurve25519Key = Curve25519Key(ownDeviceId, olmAccount.identityKeys.curve25519)

    override val sign = OlmSignService(
        ownUserId = ownUserId,
        ownDeviceId = ownDeviceId,
        json = json,
        store = store,
        account = olmAccount,
        olmUtility = olmUtility,
    )
    override val event = OlmEventService(
        olmPickleKey = olmPickleKey,
        ownUserId = ownUserId,
        ownDeviceId = ownDeviceId,
        ownEd25519Key = ownEd25519Key,
        ownCurve25519Key = ownCurve25519Key,
        json = json,
        olmAccount = olmAccount,
        store = store,
        api = api,
        signService = sign,
    )

    init {
        api.sync.subscribeDeviceOneTimeKeysCount(::handleDeviceOneTimeKeysCount)
        api.sync.subscribe(::handleMemberEvents)
        api.sync.subscribe(::handleEncryptionEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) { event.decryptedOlmEvents.collect(::handleOlmEncryptedRoomKeyEventContent) }
    }

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount?) {
        if (count == null) return
        val generateOneTimeKeysCount =
            (olmAccount.maxNumberOfOneTimeKeys / 2 - (count[KeyAlgorithm.SignedCurve25519] ?: 0))
                .coerceAtLeast(0)
        if (generateOneTimeKeysCount > 0) {
            olmAccount.generateOneTimeKeys(generateOneTimeKeysCount + olmAccount.maxNumberOfOneTimeKeys / 4)
            val signedOneTimeKeys = Keys(olmAccount.oneTimeKeys.curve25519.map {
                sign.signCurve25519Key(Curve25519Key(keyId = it.key, value = it.value))
            }.toSet())
            log.debug { "generate and upload $generateOneTimeKeysCount one time keys." }
            api.keys.setKeys(oneTimeKeys = signedOneTimeKeys).getOrThrow()
            olmAccount.markKeysAsPublished()
            store.olm.storeAccount(olmAccount, olmPickleKey)
        }
    }

    internal suspend fun handleOlmEncryptedRoomKeyEventContent(event: IOlmService.DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is RoomKeyEventContent) {
            log.debug { "got inbound megolm session for room ${content.roomId}" }
            store.olm.storeTrustedInboundMegolmSession(
                roomId = content.roomId,
                senderKey = event.encrypted.content.senderKey,
                senderSigningKey = requireNotNull(event.decrypted.senderKeys.get()),
                sessionId = content.sessionId,
                sessionKey = content.sessionKey,
                pickleKey = olmPickleKey
            )
        }
    }

    internal suspend fun handleMemberEvents(event: ClientEvent<MemberEventContent>) {
        if (event is StateEvent && store.room.get(event.roomId).value?.encryptionAlgorithm == Megolm) {
            log.debug { "handle membership change in an encrypted room" }
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
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

    internal suspend fun handleEncryptionEvents(event: ClientEvent<EncryptionEventContent>) {
        if (event is StateEvent) {
            val outdatedKeys = store.roomState.members(event.roomId, JOIN, INVITE).filterNot {
                store.keys.isTracked(it)
            }
            store.keys.outdatedKeys.update { it + outdatedKeys }
        }
    }
}