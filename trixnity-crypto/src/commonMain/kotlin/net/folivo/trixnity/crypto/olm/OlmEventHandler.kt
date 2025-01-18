package net.folivo.trixnity.crypto.olm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
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
    private val userInfo: UserInfo,
    private val eventEmitter: ClientEventEmitter<*>,
    private val olmKeysChangeEmitter: OlmKeysChangeEmitter,
    private val decrypter: OlmDecrypter,
    private val signService: SignService,
    private val requestHandler: OlmEventHandlerRequestHandler,
    private val store: OlmStore,
    private val clock: Clock,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        olmKeysChangeEmitter.subscribeOneTimeKeysCount(::handleOlmKeysChange).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(subscriber = ::handleMemberEvents).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEvent(subscriber = ::handleHistoryVisibility).unsubscribeOnCompletion(scope)
        eventEmitter.subscribeEventList(Priority.TO_DEVICE_EVENTS, ::handleOlmEvents).unsubscribeOnCompletion(scope)
        eventEmitter.subscribe(Priority.LAST, ::forgetOldFallbackKey).unsubscribeOnCompletion(scope)
        decrypter.subscribe(::handleOlmEncryptedRoomKeyEventContent).unsubscribeOnCompletion(scope)
        scope.launch {
            forgetOldFallbackKey()
        }
    }

    internal suspend fun handleOlmEvents(events: List<ToDeviceEvent<OlmEncryptedToDeviceEventContent>>) =
        decrypter.handleOlmEvents(events)

    internal suspend fun forgetOldFallbackKey() {
        val forgetFallbackKeyAfter = store.getForgetFallbackKeyAfter().first()
        if (forgetFallbackKeyAfter != null && forgetFallbackKeyAfter < clock.now()) {
            log.debug { "forget old fallback key" }
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

    internal suspend fun handleOlmKeysChange(change: OlmKeysChange) {
        val oneTimeKeysCount = change.oneTimeKeysCount
        val fallbackKeyTypes = change.fallbackKeyTypes
        var newOneTimeKeys: Keys? = null
        var newFallbackKeys: Keys? = null

        log.trace { "handle change of own olm keys server count" }
        store.updateOlmAccount { pickledOlmAccount ->
            freeAfter(
                OlmAccount.unpickle(store.getOlmPickleKey(), pickledOlmAccount)
            ) { olmAccount ->
                newOneTimeKeys = olmAccount.oneTimeKeys.curve25519.toCurve25519Keys()
                if (newOneTimeKeys != null) log.trace { "found one time keys marked as unpublished" }
                if (newOneTimeKeys.isNullOrEmpty() && oneTimeKeysCount != null) {
                    val generateOneTimeKeysCount =
                        (olmAccount.maxNumberOfOneTimeKeys / 2 - (oneTimeKeysCount[KeyAlgorithm.SignedCurve25519]
                            ?: 0))
                            .coerceAtLeast(0)
                    if (generateOneTimeKeysCount > 0) {
                        log.trace { "generate $generateOneTimeKeysCount new one time key" }
                        olmAccount.generateOneTimeKeys(generateOneTimeKeysCount + olmAccount.maxNumberOfOneTimeKeys / 4)
                        newOneTimeKeys = olmAccount.oneTimeKeys.curve25519.toCurve25519Keys()
                    } else null
                } else null

                newFallbackKeys = olmAccount.unpublishedFallbackKey.curve25519.toCurve25519Keys(fallback = true)
                if (newFallbackKeys != null) log.trace { "found fallback key marked as unpublished" }
                if (newFallbackKeys.isNullOrEmpty()
                    && fallbackKeyTypes?.contains(KeyAlgorithm.SignedCurve25519)?.not() == true
                ) {
                    log.trace { "generate new fallback key" }
                    olmAccount.generateFallbackKey()
                    newFallbackKeys = olmAccount.unpublishedFallbackKey.curve25519.toCurve25519Keys(fallback = true)
                } else null
                olmAccount.pickle(store.getOlmPickleKey())
            }
        }
        if (newOneTimeKeys != null || newFallbackKeys != null) {
            log.debug { "upload ${newOneTimeKeys?.size ?: 0} one time keys and ${newFallbackKeys?.size ?: 0} fallback keys." }
            requestHandler.setOneTimeKeys(
                oneTimeKeys = newOneTimeKeys,
                fallbackKeys = newFallbackKeys
            ).onFailure {
                if (it is MatrixServerException && it.statusCode.value in (400 until 500)) {
                    log.warn(it) { "seems like our keys were already uploaded, so just marking them as published" }
                } else throw it
            }

            store.updateOlmAccount { pickledOlmAccount ->
                freeAfter(
                    OlmAccount.unpickle(store.getOlmPickleKey(), pickledOlmAccount)
                ) { olmAccount ->
                    log.trace { "mark keys as published" }
                    olmAccount.markKeysAsPublished()
                    olmAccount.pickle(store.getOlmPickleKey())
                }
            }

            newFallbackKeys // we can forget the old fallback key, when we had to generate a new one and successfully set it
                ?.also {
                    val forgetAfter = clock.now() + 1.hours
                    log.trace { "mark fallback key to be forget after $forgetAfter" }
                    store.updateForgetFallbackKeyAfter { clock.now() + 1.hours }
                }
        }
        log.trace { "finished handle change of own olm keys server count" }
    }

    private suspend fun Map<String, String>.toCurve25519Keys(fallback: Boolean = false) =
        Keys(this.map {
            signService.signCurve25519Key(
                Curve25519Key(keyId = it.key, value = it.value, fallback = fallback)
            )
        }.toSet()).ifEmpty { null }

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

    internal suspend fun handleMemberEvents(events: List<StateEvent<MemberEventContent>>) = coroutineScope {
        events.forEach { event ->
            val roomId = event.roomId
            val userId = UserId(event.stateKey)
            if (userId != userInfo.userId && store.getRoomEncryptionAlgorithm(roomId) == EncryptionAlgorithm.Megolm) {
                val membership = event.content.membership
                val membershipsAllowedToReceiveKey: Set<Membership> =
                    store.getHistoryVisibility(roomId).membershipsAllowedToReceiveKey
                if (membershipsAllowedToReceiveKey.contains(membership)) {
                    store.updateOutboundMegolmSession(roomId) {
                        if (it != null) {
                            log.debug { "add new devices of $userId to megolm session of $roomId, because new membership does allow to share key" }
                            val devices = store.getDevices(roomId, userId)
                            if (!devices.isNullOrEmpty())
                                it.copy(newDevices = it.newDevices + (userId to devices))
                            else it
                        } else null
                    }
                } else {
                    log.debug { "reset megolm session of $roomId, because new membership does not allow share key" }
                    store.updateOutboundMegolmSession(roomId) { null }
                }
            }
        }
    }

    internal suspend fun handleHistoryVisibility(event: StateEvent<HistoryVisibilityEventContent>) {
        log.debug { "reset megolm session, because visibility has changed in ${event.roomId}" }
        if (store.getRoomEncryptionAlgorithm(event.roomId) == EncryptionAlgorithm.Megolm)
            store.updateOutboundMegolmSession(event.roomId) { null }
    }
}