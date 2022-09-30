package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.clientserverapi.model.sync.DeviceOneTimeKeysCount
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.IEventEmitter
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.sign.ISignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.olm.freeAfter

private val log = KotlinLogging.logger {}

class OlmEventHandler(
    private val eventEmitter: IEventEmitter,
    private val oneTimeKeysCountEmitter: OneTimeKeysCountEmitter,
    private val decrypter: IOlmDecrypter,
    private val signService: ISignService,
    private val requestHandler: OlmEventHandlerRequestHandler,
    private val store: OlmStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        oneTimeKeysCountEmitter.subscribeDeviceOneTimeKeysCount(::handleDeviceOneTimeKeysCount)
        eventEmitter.subscribe(::handleMemberEvents)
        eventEmitter.subscribe(decrypter::handleOlmEvent)
        decrypter.subscribe(::handleOlmEncryptedRoomKeyEventContent)
        scope.coroutineContext.job.invokeOnCompletion {
            oneTimeKeysCountEmitter.unsubscribeDeviceOneTimeKeysCount(::handleDeviceOneTimeKeysCount)
            eventEmitter.unsubscribe(::handleMemberEvents)
            eventEmitter.unsubscribe(decrypter::handleOlmEvent)
            decrypter.unsubscribe(::handleOlmEncryptedRoomKeyEventContent)
        }
    }

    internal suspend fun handleDeviceOneTimeKeysCount(count: DeviceOneTimeKeysCount?) {
        if (count == null) return
        store.olmAccount.update { pickledOlmAccount ->
            freeAfter(
                OlmAccount.unpickle(
                    store.olmPickleKey, requireNotNull(pickledOlmAccount) { "olm account should never be null" })
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
                    olmAccount.pickle(store.olmPickleKey)
                } else pickledOlmAccount
            }
        }
    }

    internal suspend fun handleOlmEncryptedRoomKeyEventContent(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is RoomKeyEventContent) {
            log.debug { "got inbound megolm session for room ${content.roomId}" }
            val senderSigningKey = event.decrypted.senderKeys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            if (senderSigningKey == null) {
                log.warn { "ignore inbound megolm session because it did not contain any sender signing key" }
                return
            }
            store.updateInboundMegolmSession(content.sessionId, content.roomId) {
                it
                    ?: try {
                        freeAfter(
                            OlmInboundGroupSession.create(content.sessionKey)
                        ) { session ->
                            StoredInboundMegolmSession(
                                senderKey = event.encrypted.content.senderKey,
                                senderSigningKey = senderSigningKey,
                                sessionId = content.sessionId,
                                roomId = content.roomId,
                                firstKnownIndex = session.firstKnownIndex,
                                hasBeenBackedUp = false,
                                isTrusted = true,
                                forwardingCurve25519KeyChain = emptyList(),
                                pickled = session.pickle(store.olmPickleKey)
                            )
                        }
                    } catch (exception: OlmLibraryException) {
                        log.warn { "ignore inbound megolm session due to: ${exception.message}" }
                        null
                    }
            }

        }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) {
        if (event is StateEvent && store.getRoomEncryptionAlgorithm(event.roomId) == Megolm) {
            log.debug { "remove outbound megolm session" }
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
                    store.updateOutboundMegolmSession(event.roomId) { null }
                }

                else -> {
                }
            }
        }
    }
}