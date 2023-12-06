package net.folivo.trixnity.crypto.olm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.hours

private val log = KotlinLogging.logger {}

class OlmEventHandler(
    private val eventEmitter: ClientEventEmitter<*>,
    private val olmKeysChangeEmitter: OlmKeysChangeEmitter,
    private val decrypter: OlmDecrypter,
    private val signService: SignService,
    private val requestHandler: OlmEventHandlerRequestHandler,
    private val store: OlmStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmKeysChangeEmitter.subscribeOneTimeKeysCount(::handleOlmKeysChange).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(subscriber = ::handleMemberEvents).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEvent(subscriber = ::handleHistoryVisibility).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(Priority.TO_DEVICE_EVENTS, ::handleOlmEvents).unsubscribeOnCompletion(scope)
        decrypter.subscribe(::handleOlmEncryptedRoomKeyEventContent).unsubscribeOnCompletion(scope)
        scope.launch {
            forgetOldFallbackKey()
        }
    }

    internal suspend fun handleOlmEvents(events: List<ToDeviceEvent<OlmEncryptedToDeviceEventContent>>) =
        decrypter.handleOlmEvents(events)

    internal suspend fun forgetOldFallbackKey() {
        store.getForgetFallbackKeyAfter().collect { forgetFallbackKeyAfter ->
            if (forgetFallbackKeyAfter != null) {
                val wait = forgetFallbackKeyAfter - Clock.System.now()
                log.debug { "wait for $wait and then forget old fallback key" }
                delay(wait)
                store.updateOlmAccount { pickledOlmAccount ->
                    freeAfter(
                        OlmAccount.unpickle(store.getOlmPickleKey(), pickledOlmAccount),
                    ) { olmAccount ->
                        olmAccount.forgetOldFallbackKey()
                        olmAccount.pickle(store.getOlmPickleKey())
                    }
                }
                store.updateForgetFallbackKeyAfter { null }
            }
        }
    }

    internal suspend fun handleOlmKeysChange(change: OlmKeysChange) {
        val oneTimeKeysCount = change.oneTimeKeysCount
        val fallbackKeyTypes = change.fallbackKeyTypes
        store.updateOlmAccount { pickledOlmAccount ->
            freeAfter(
                OlmAccount.unpickle(store.getOlmPickleKey(), pickledOlmAccount)
            ) { olmAccount ->
                val newOneTimeKeys =
                    if (oneTimeKeysCount != null) {
                        val generateOneTimeKeysCount =
                            (olmAccount.maxNumberOfOneTimeKeys / 2 - (oneTimeKeysCount[KeyAlgorithm.SignedCurve25519]
                                ?: 0))
                                .coerceAtLeast(0)
                        if (generateOneTimeKeysCount > 0) {
                            olmAccount.generateOneTimeKeys(generateOneTimeKeysCount + olmAccount.maxNumberOfOneTimeKeys / 4)
                            Keys(olmAccount.oneTimeKeys.curve25519.map {
                                signService.signCurve25519Key(Curve25519Key(keyId = it.key, value = it.value))
                            }.toSet())
                        } else null
                    } else null

                val newFallbackKeys =
                    if (fallbackKeyTypes?.contains(KeyAlgorithm.SignedCurve25519)?.not() == true) {
                        olmAccount.generateFallbackKey()
                        Keys(olmAccount.unpublishedFallbackKey.curve25519.map {
                            signService.signCurve25519Key(
                                Curve25519Key(keyId = it.key, value = it.value, fallback = true)
                            )
                        }.toSet())
                            .also { store.updateForgetFallbackKeyAfter { it ?: (Clock.System.now() + 1.hours) } }
                    } else null

                if (newOneTimeKeys != null || newFallbackKeys != null) {
                    log.debug { "generate and upload ${newOneTimeKeys?.size} one time keys and ${newFallbackKeys?.size} fallback keys." }
                    requestHandler.setOneTimeKeys(
                        oneTimeKeys = newOneTimeKeys,
                        fallbackKeys = newFallbackKeys
                    ).getOrThrow()
                    olmAccount.markKeysAsPublished()
                    olmAccount.pickle(store.getOlmPickleKey())
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
                                pickled = session.pickle(store.getOlmPickleKey())
                            )
                        }
                    } catch (exception: OlmLibraryException) {
                        log.warn { "ignore inbound megolm session due to: ${exception.message}" }
                        null
                    }
            }

        }
    }

    internal suspend fun handleMemberEvents(events: List<StateEvent<MemberEventContent>>) {
        events
            .filter {
                when (it.content.membership) {
                    Membership.LEAVE, Membership.BAN -> true
                    else -> false
                }
            }
            .map { it.roomId }
            .toSet()
            .forEach { roomId ->
                log.debug { "reset megolm session, because LEAVE or BAN received" }
                store.updateOutboundMegolmSession(roomId) { null }
            }
    }

    internal suspend fun handleHistoryVisibility(event: StateEvent<HistoryVisibilityEventContent>) {
        log.debug { "reset megolm session, because visibility has changed in ${event.roomId}" }
        store.updateOutboundMegolmSession(event.roomId) { null }
    }
}