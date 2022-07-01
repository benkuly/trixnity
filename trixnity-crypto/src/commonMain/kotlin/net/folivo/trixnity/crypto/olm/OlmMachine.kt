package net.folivo.trixnity.crypto.olm

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.clientserverapi.model.sync.DeviceOneTimeKeysCount
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.sign.ISignService
import net.folivo.trixnity.crypto.sign.sign
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.olm.freeAfter

private val log = KotlinLogging.logger {}

interface IOlmMachine {
    val event: IOlmEventService
    suspend fun start()

    suspend fun stop()
    suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId>
}

class OlmMachine private constructor(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val ownEd25519Key: Ed25519Key,
    private val ownCurve25519Key: Curve25519Key,
    private val eventEmitter: EventEmitter,
    private val oneTimeKeysCountEmitter: OneTimeKeysCountEmitter,
    private val requestHandler: OlmMachineRequestHandler,
    private val signService: ISignService,
    private val store: OlmMachineStore,
    private val olmPickleKey: String,
    json: Json,
) : IOlmMachine {

    companion object {
        suspend fun create(
            ownUserId: UserId,
            ownDeviceId: String,
            eventEmitter: EventEmitter,
            oneTimeKeysCountEmitter: OneTimeKeysCountEmitter,
            requestHandler: OlmMachineRequestHandler,
            signService: ISignService,
            store: OlmMachineStore,
            json: Json,
            olmPickleKey: String,
        ): OlmMachine {
            val (ownEd25519Key, ownCurve25519Key) = freeAfter(
                OlmAccount.unpickle(
                    olmPickleKey,
                    requireNotNull(store.getOlmAccount()) { "olm account should never be null" })
            ) {
                Ed25519Key(ownDeviceId, it.identityKeys.ed25519) to
                        Curve25519Key(ownDeviceId, it.identityKeys.curve25519)
            }
            return OlmMachine(
                ownUserId = ownUserId,
                ownDeviceId = ownDeviceId,
                ownEd25519Key = ownEd25519Key,
                ownCurve25519Key = ownCurve25519Key,
                eventEmitter = eventEmitter,
                oneTimeKeysCountEmitter = oneTimeKeysCountEmitter,
                requestHandler = requestHandler,
                signService = signService,
                store = store,
                json = json,
                olmPickleKey = olmPickleKey,
            )
        }
    }


    override val event = OlmEventService(
        olmPickleKey = olmPickleKey,
        ownUserId = ownUserId,
        ownDeviceId = ownDeviceId,
        ownEd25519Key = ownEd25519Key,
        ownCurve25519Key = ownCurve25519Key,
        json = json,
        store = store,
        requests = requestHandler,
        signService = signService,
    )

    private val olmDecrypter = OlmDecrypter(event, ::handleOlmEncryptedRoomKeyEventContent)

    override suspend fun start() {
        oneTimeKeysCountEmitter.subscribeDeviceOneTimeKeysCount(::handleDeviceOneTimeKeysCount)
        eventEmitter.subscribe(::handleMemberEvents)
        eventEmitter.subscribe(olmDecrypter)
    }

    override suspend fun stop() {
        oneTimeKeysCountEmitter.unsubscribeDeviceOneTimeKeysCount(::handleDeviceOneTimeKeysCount)
        eventEmitter.unsubscribe(::handleMemberEvents)
        eventEmitter.unsubscribe(olmDecrypter)
    }

    override suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId> = signService.sign(
        DeviceKeys(
            userId = ownUserId,
            deviceId = ownDeviceId,
            algorithms = setOf(Olm, Megolm),
            keys = Keys(keysOf(ownEd25519Key, ownCurve25519Key))
        )
    )

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount?) {
        if (count == null) return
        store.updateOlmAccount { pickledOlmAccount ->
            freeAfter(
                OlmAccount.unpickle(
                    olmPickleKey, requireNotNull(pickledOlmAccount) { "olm account should never be null" })
            ) { olmAccount ->
                val generateOneTimeKeysCount =
                    (olmAccount.maxNumberOfOneTimeKeys / 2 - (count[KeyAlgorithm.SignedCurve25519] ?: 0))
                        .coerceAtLeast(0)
                if (generateOneTimeKeysCount > 0) {
                    olmAccount.generateOneTimeKeys(generateOneTimeKeysCount + olmAccount.maxNumberOfOneTimeKeys / 4)
                    val signedOneTimeKeys = Keys(olmAccount.oneTimeKeys.curve25519.map {
                        signService.signCurve25519Key(Curve25519Key(keyId = it.key, value = it.value))
                    }.toSet())
                    log.debug { "generate and upload $generateOneTimeKeysCount one time keys." }
                    requestHandler.setOneTimeKeys(oneTimeKeys = signedOneTimeKeys).getOrThrow()
                    olmAccount.markKeysAsPublished()
                    UpdateResult(olmAccount.pickle(olmPickleKey), Unit)
                } else UpdateResult(pickledOlmAccount, Unit)
            }
        }
    }

    internal suspend fun handleOlmEncryptedRoomKeyEventContent(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is RoomKeyEventContent) {
            log.debug { "got inbound megolm session for room ${content.roomId}" }
            val senderSigningKey = event.decrypted.senderKeys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            if (senderSigningKey == null) {
                log.debug { "ignore inbound megolm session because it did not contain any sender signing key" }
                return
            }
            store.updateInboundMegolmSession(content.sessionId, content.roomId) {
                UpdateResult(
                    it
                        ?: try {
                            freeAfter(
                                OlmInboundGroupSession.create(content.sessionKey)
                            ) { session ->
                                StoredInboundMegolmSession(
                                    senderKey = event.encrypted.content.senderKey,
                                    sessionId = content.sessionId,
                                    roomId = content.roomId,
                                    firstKnownIndex = session.firstKnownIndex,
                                    hasBeenBackedUp = false,
                                    isTrusted = true,
                                    senderSigningKey = senderSigningKey,
                                    forwardingCurve25519KeyChain = emptyList(),
                                    pickled = session.pickle(olmPickleKey)
                                )
                            }
                        } catch (exception: OlmLibraryException) {
                            log.debug { "ignore inbound megolm session due to: ${exception.message}" }
                            null
                        }, Unit
                )
            }

        }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) { // FIXME test
        if (event is StateEvent && store.getRoomEncryptionAlgorithm(event.roomId) == Megolm) {
            log.debug { "remove outbound megolm session" }
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
                    store.updateOutboundMegolmSession(event.roomId) { UpdateResult(null, Unit) }
                }
                else -> {
                }
            }
        }
    }
}