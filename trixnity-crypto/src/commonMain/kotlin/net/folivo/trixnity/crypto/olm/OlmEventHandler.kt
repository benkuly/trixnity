package net.folivo.trixnity.crypto.olm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.clientserverapi.client.OlmKeysChange
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.hours

private val log = KotlinLogging.logger {}

class OlmEventHandler(
    private val eventEmitter: EventEmitter,
    private val olmKeysChangeEmitter: OlmKeysChangeEmitter,
    private val decrypter: OlmDecrypter,
    private val signService: SignService,
    private val requestHandler: OlmEventHandlerRequestHandler,
    private val store: OlmStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmKeysChangeEmitter.subscribeOneTimeKeysCount(::handleOlmKeysChange)
        eventEmitter.subscribe(::handleMemberEvents)
        eventEmitter.subscribe(::handleHistoryVisibility)
        eventEmitter.subscribe(decrypter::handleOlmEvent)
        decrypter.subscribe(::handleOlmEncryptedRoomKeyEventContent)
        scope.launch {
            forgetOldFallbackKey()
        }
        scope.coroutineContext.job.invokeOnCompletion {
            olmKeysChangeEmitter.unsubscribeOneTimeKeysCount(::handleOlmKeysChange)
            eventEmitter.unsubscribe(::handleMemberEvents)
            eventEmitter.unsubscribe(::handleHistoryVisibility)
            eventEmitter.unsubscribe(decrypter::handleOlmEvent)
            decrypter.unsubscribe(::handleOlmEncryptedRoomKeyEventContent)
        }
    }

    internal suspend fun forgetOldFallbackKey() {
        store.forgetFallbackKeyAfter.collect { forgetFallbackKeyAfter ->
            if (forgetFallbackKeyAfter != null) {
                val wait = forgetFallbackKeyAfter - Clock.System.now()
                log.debug { "wait for $wait and then forget old fallback key" }
                delay(wait)
                store.olmAccount.update { pickledOlmAccount ->
                    freeAfter(
                        OlmAccount.unpickle(
                            store.olmPickleKey,
                            requireNotNull(pickledOlmAccount) { "olm account should never be null" },
                        ),
                    ) { olmAccount ->
                        olmAccount.forgetOldFallbackKey()
                        olmAccount.pickle(store.olmPickleKey)
                    }
                }
                store.forgetFallbackKeyAfter.value = null
            }
        }
    }

    internal suspend fun handleOlmKeysChange(change: OlmKeysChange) {
        val oneTimeKeysCount = change.oneTimeKeysCount
        val fallbackKeyTypes = change.fallbackKeyTypes
        store.olmAccount.update { pickledOlmAccount ->
            freeAfter(
                OlmAccount.unpickle(
                    store.olmPickleKey, requireNotNull(pickledOlmAccount) { "olm account should never be null" })
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
                            .also { store.forgetFallbackKeyAfter.update { it ?: (Clock.System.now() + 1.hours) } }
                    } else null

                if (newOneTimeKeys != null || newFallbackKeys != null) {
                    log.debug { "generate and upload ${newOneTimeKeys?.size} one time keys and ${newFallbackKeys?.size} fallback keys." }
                    requestHandler.setOneTimeKeys(
                        oneTimeKeys = newOneTimeKeys,
                        fallbackKeys = newFallbackKeys
                    ).getOrThrow()
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

    internal suspend fun handleHistoryVisibility(event: Event<HistoryVisibilityEventContent>) {
        if (event is StateEvent) {
            log.debug { "reset megolm session, because visibility has changed in ${event.roomId}" }
            store.updateOutboundMegolmSession(event.roomId) { null }
        }
    }
}