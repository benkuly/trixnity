package net.folivo.trixnity.crypto.olm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.Companion.MILLISECOND
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.*
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.*
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.crypto.sign.verify
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.ORDINARY
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private val log = KotlinLogging.logger {}

interface OlmEncryptionService {

    sealed interface EncryptOlmError {
        data class OlmLibraryError(
            val error: OlmLibraryException,
        ) : EncryptOlmError, IllegalStateException("error while encrypting with olm", error)

        data class KeyNotFound(
            val keyAlgorithm: KeyAlgorithm,
        ) : EncryptOlmError, IllegalStateException("$keyAlgorithm key not found while encrypting with olm")

        data class OneTimeKeyNetworkError(
            val error: Throwable,
        ) : EncryptOlmError,
            IllegalStateException("network error while fetching one time keys while encrypting with olm", error)

        data class OneTimeKeyRemoteServerError(
            val keyAlgorithm: KeyAlgorithm,
            val server: String,
        ) : EncryptOlmError,
            IllegalStateException("remote server error while fetching one time $keyAlgorithm keys while encrypting with olm")

        data class OneTimeKeyVerificationFailed(
            val keyAlgorithm: KeyAlgorithm,
            val verifyResult: VerifyResult,
        ) : EncryptOlmError,
            IllegalStateException("validation of $keyAlgorithm one time key failed while encrypting with olm ($verifyResult)")

        data class OneTimeKeyNotFound(
            val keyAlgorithm: KeyAlgorithm,
        ) : EncryptOlmError, IllegalStateException("no $keyAlgorithm one time key found while encrypting with olm")
    }

    suspend fun encryptOlm(
        content: EventContent,
        userId: UserId,
        deviceId: String,
        forceNewSession: Boolean = false,
    ): Result<OlmEncryptedToDeviceEventContent>

    sealed interface DecryptOlmError {
        data class OlmLibraryError(
            val error: OlmLibraryException,
        ) : DecryptOlmError, IllegalStateException("error while decrypting with olm", error)

        data class KeyNotFound(
            val keyAlgorithm: KeyAlgorithm,
        ) : DecryptOlmError, IllegalStateException("$keyAlgorithm key not found while decrypting with olm")

        object SenderDidNotEncryptForThisDeviceException : DecryptOlmError,
            IllegalStateException("no ciphertext found for this device while decrypting with olm")

        object NoMatchingOlmSessionFound : DecryptOlmError,
            IllegalStateException("no matching olm session found while decrypting with olm")

        object TooManySessions : DecryptOlmError,
            IllegalStateException("too many sessions created while decrypting with olm")

        data class ValidationFailed(
            val reason: String,
        ) : DecryptOlmError, IllegalStateException("validation failed while decrypting with olm ($reason)")

        data class DeserializationError(
            val error: SerializationException,
        ) : DecryptOlmError, IllegalStateException("deserialization failed while decrypting with olm", error)
    }

    suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>,
    ): Result<DecryptedOlmEvent<*>>

    sealed interface EncryptMegolmError {
        data class OlmLibraryError(
            val error: OlmLibraryException,
        ) : EncryptMegolmError, IllegalStateException("error while encrypting with megolm", error)
    }

    suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): Result<MegolmEncryptedMessageEventContent>


    sealed interface DecryptMegolmError {
        data class OlmLibraryError(
            val error: OlmLibraryException,
        ) : DecryptMegolmError, IllegalStateException("error while decrypting with megolm", error)

        data object MegolmKeyNotFound : DecryptMegolmError,
            IllegalStateException("megolm key not found while decrypting with megolm")

        data object MegolmKeyUnknownMessageIndex : DecryptMegolmError,
            IllegalStateException("megolm key with unknown message index while decrypting with megolm")

        data class ValidationFailed(
            val reason: String,
        ) : DecryptMegolmError, IllegalStateException("validation failed while decrypting with megolm ($reason)")

        data class DeserializationError(
            val error: SerializationException,
        ) : DecryptMegolmError, IllegalStateException("deserialization failed while decrypting with megolm", error)
    }

    suspend fun decryptMegolm(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>>
}

class OlmEncryptionServiceImpl(
    userInfo: UserInfo,
    private val json: Json,
    private val store: OlmStore,
    private val requests: OlmEncryptionServiceRequestHandler,
    private val signService: SignService,
) : OlmEncryptionService {

    private val ownUserId: UserId = userInfo.userId
    private val ownDeviceId: String = userInfo.deviceId
    private val ownEd25519Key: Ed25519Key = userInfo.signingPublicKey
    private val ownCurve25519Key: Curve25519Key = userInfo.identityPublicKey

    @OptIn(ExperimentalContracts::class)
    private suspend fun withStoredSessions(
        identityKey: Curve25519Key,
        block: suspend (Set<StoredOlmSession>?) -> StoredOlmSession
    ) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        store.updateOlmSessions(identityKey) { storedSessions ->
            val newStoredSession = block(storedSessions)
            storedSessions.addOrUpdateNewAndRemoveOldSessions(newStoredSession)
        }
    }

    override suspend fun encryptOlm(
        content: EventContent,
        userId: UserId,
        deviceId: String,
        forceNewSession: Boolean,
    ): Result<OlmEncryptedToDeviceEventContent> = runCatching {
        val identityKey = store.findCurve25519Key(userId, deviceId)
            ?: throw EncryptOlmError.KeyNotFound(KeyAlgorithm.Curve25519)
        val signingKey = store.findEd25519Key(userId, deviceId)
            ?: throw EncryptOlmError.KeyNotFound(KeyAlgorithm.Ed25519)

        lateinit var encryptionResult: OlmEncryptedToDeviceEventContent
        withStoredSessions(identityKey) { storedSessions ->
            val lastUsedOlmStoredOlmSessions = storedSessions?.maxByOrNull { it.lastUsedAt }

            @OptIn(ExperimentalSerializationApi::class)
            fun encryptWithOlmSession(
                olmSession: OlmSession,
                content: EventContent,
                userId: UserId,
                identityKey: Curve25519Key,
                signingKey: Ed25519Key,
            ): OlmEncryptedToDeviceEventContent {
                val serializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
                val event = DecryptedOlmEvent(
                    content = content,
                    sender = ownUserId,
                    senderKeys = keysOf(ownEd25519Key.copy(keyId = null)),
                    recipient = userId,
                    recipientKeys = keysOf(signingKey.copy(keyId = null))
                ).also { log.trace { "olm event: $it" } }
                checkNotNull(serializer)
                val encryptedContent = olmSession.encrypt(json.encodeToString(serializer, event))
                return OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        identityKey.value to OlmEncryptedToDeviceEventContent.CiphertextInfo(
                            encryptedContent.cipherText,
                            OlmMessageType.of(encryptedContent.type.value)
                        )
                    ),
                    senderKey = ownCurve25519Key,
                )
            }

            if (lastUsedOlmStoredOlmSessions == null || forceNewSession) {
                log.debug { "encrypt olm event with new session for device with key $identityKey" }
                val response =
                    requests.claimKeys(mapOf(userId to mapOf(deviceId to KeyAlgorithm.SignedCurve25519)))
                        .fold(
                            onFailure = { throw EncryptOlmError.OneTimeKeyNetworkError(it) },
                            onSuccess = { it }
                        )
                if (response.failures.isNotEmpty())
                    throw EncryptOlmError.OneTimeKeyRemoteServerError(
                        KeyAlgorithm.SignedCurve25519,
                        response.failures.keys.first()
                    )
                val oneTimeKey =
                    response.oneTimeKeys[userId]?.get(deviceId)?.keys?.firstOrNull() as? SignedCurve25519Key
                        ?: throw EncryptOlmError.OneTimeKeyNotFound(KeyAlgorithm.SignedCurve25519)
                val keyVerifyState = signService.verify(oneTimeKey, mapOf(userId to setOf(signingKey)))
                if (keyVerifyState is VerifyResult.Invalid)
                    throw EncryptOlmError.OneTimeKeyVerificationFailed(
                        KeyAlgorithm.SignedCurve25519,
                        keyVerifyState
                    )
                try {
                    val olmAccount = OlmAccount.unpickle(store.getOlmPickleKey(), store.getOlmAccount())
                    freeAfter(
                        olmAccount,
                        OlmSession.createOutbound(
                            account = olmAccount,
                            theirIdentityKey = identityKey.value,
                            theirOneTimeKey = oneTimeKey.signed.value
                        )
                    ) { _, session ->
                        encryptionResult = encryptWithOlmSession(
                            olmSession = session,
                            content = content,
                            userId = userId,
                            identityKey = identityKey,
                            signingKey = signingKey,
                        )
                        StoredOlmSession(
                            sessionId = session.sessionId,
                            senderKey = identityKey,
                            pickled = session.pickle(store.getOlmPickleKey()),
                            lastUsedAt = Clock.System.now()
                        )
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    throw EncryptOlmError.OlmLibraryError(olmLibraryException)
                }
            } else {
                log.debug { "encrypt olm event with existing session for device with key $identityKey" }
                try {
                    freeAfter(
                        OlmSession.unpickle(
                            store.getOlmPickleKey(),
                            lastUsedOlmStoredOlmSessions.pickled
                        )
                    ) { session ->
                        encryptionResult = encryptWithOlmSession(
                            olmSession = session,
                            content = content,
                            userId = userId,
                            identityKey = identityKey,
                            signingKey = signingKey,
                        )
                        StoredOlmSession(
                            sessionId = session.sessionId,
                            senderKey = identityKey,
                            pickled = session.pickle(store.getOlmPickleKey()),
                            lastUsedAt = Clock.System.now()
                        )
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    throw EncryptOlmError.OlmLibraryError(olmLibraryException)
                }
            }
        }
        encryptionResult
            .also { log.trace { "encrypted event: $it" } }
    }.onFailure { log.warn(it) { "encrypt olm failed" } }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptOlm(
        event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>,
    ): Result<DecryptedOlmEvent<*>> = runCatching {
        log.debug { "start decrypt olm event $event" }
        val encryptedContent = event.content
        val userId = event.sender
        val senderIdentityKey = encryptedContent.senderKey
        val senderDeviceKeys = store.findDeviceKeys(userId, senderIdentityKey)
            ?: throw DecryptOlmError.KeyNotFound(KeyAlgorithm.Ed25519)
        val deviceId = senderDeviceKeys.deviceId
        val ciphertext = encryptedContent.ciphertext[ownCurve25519Key.value]
            ?: throw DecryptOlmError.SenderDidNotEncryptForThisDeviceException
        val senderSigningKey = senderDeviceKeys.keys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            ?: throw DecryptOlmError.KeyNotFound(KeyAlgorithm.Ed25519)

        lateinit var decryptionResult: DecryptedOlmEvent<*>
        withStoredSessions(senderIdentityKey) { storedSessions ->

            fun decryptWithOlmSession(
                decrypted: String
            ): DecryptedOlmEvent<*> {
                val serializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
                checkNotNull(serializer)
                val decryptedEvent = try {
                    json.decodeFromString(serializer, decrypted)
                } catch (exception: SerializationException) {
                    throw DecryptOlmError.DeserializationError(exception)
                }

                return when {
                    decryptedEvent.sender != userId ->
                        throw DecryptOlmError.ValidationFailed("sender did not match")

                    decryptedEvent.recipient != ownUserId ->
                        throw DecryptOlmError.ValidationFailed("recipient did not match")

                    decryptedEvent.recipientKeys.filterIsInstance<Ed25519Key>()
                        .firstOrNull()?.value != ownEd25519Key.value ->
                        throw DecryptOlmError.ValidationFailed("recipientKeys did not match")

                    decryptedEvent.senderKeys.filterIsInstance<Ed25519Key>()
                        .firstOrNull()?.value != senderSigningKey.value ->
                        throw DecryptOlmError.ValidationFailed("senderKeys did not match")

                    else -> decryptedEvent
                }
            }

            suspend fun createRecoveryOlmSession(storedSessions: Set<StoredOlmSession>?) {
                if (!hasCreatedTooManyOlmSessions(storedSessions)) {
                    encryptOlm(DummyEventContent, userId, deviceId, true)
                        .fold(
                            onSuccess = { dummyEvent ->
                                log.info { "try recover corrupted olm session by sending a dummy event (userId=$userId, deviceId=$deviceId)" }
                                requests.sendToDevice(mapOf(userId to mapOf(deviceId to dummyEvent)))
                                    .onFailure { log.warn(it) { "failed sending dummy event (userId=$userId, deviceId=$deviceId)" } }
                            },
                            onFailure = { log.warn(it) { "could not encrypt dummy event (userId=$userId, deviceId=$deviceId)" } }
                        )
                }
            }

            storedSessions?.sortedByDescending { it.lastUsedAt }?.firstNotNullOfOrNull { storedSession ->
                try {
                    freeAfter(
                        OlmSession.unpickle(
                            store.getOlmPickleKey(),
                            storedSession.pickled
                        )
                    ) { olmSession ->
                        if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                            if (olmSession.matchesInboundSession(ciphertext.body)) {
                                log.debug { "try decrypt initial olm event with matching session ${storedSession.sessionId} for device with key $senderIdentityKey" }
                                olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                            } else {
                                log.debug { "initial olm event did not match session ${storedSession.sessionId} for device with key $senderIdentityKey" }
                                null
                            }
                        } else {
                            try {
                                log.debug { "try decrypt ordinary olm event with matching session ${storedSession.sessionId} for device with key $senderIdentityKey" }
                                olmSession.decrypt(OlmMessage(ciphertext.body, ORDINARY))
                            } catch (error: Throwable) {
                                log.debug { "could not decrypt olm event with existing session ${storedSession.sessionId} for device with key $senderIdentityKey. Reason: ${error.message}" }
                                null
                            }
                        }?.let { decrypted ->
                            decryptionResult = decryptWithOlmSession(decrypted)
                            StoredOlmSession(
                                sessionId = olmSession.sessionId,
                                senderKey = senderIdentityKey,
                                pickled = olmSession.pickle(store.getOlmPickleKey()),
                                lastUsedAt = Clock.System.now()
                            )
                        }
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    createRecoveryOlmSession(storedSessions)
                    throw DecryptOlmError.OlmLibraryError(olmLibraryException)
                }
            } ?: if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                if (hasCreatedTooManyOlmSessions(storedSessions))
                    throw DecryptOlmError.TooManySessions
                log.debug { "decrypt olm event with new session for device with key $senderIdentityKey" }
                lateinit var newStoredOlmSession: StoredOlmSession
                try {
                    store.updateOlmAccount {
                        val olmAccount = OlmAccount.unpickle(store.getOlmPickleKey(), it)
                        freeAfter(
                            olmAccount,
                            OlmSession.createInboundFrom(
                                olmAccount,
                                senderIdentityKey.value,
                                ciphertext.body
                            )
                        ) { _, olmSession ->
                            val decrypted = olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                            olmAccount.removeOneTimeKeys(olmSession)
                            decryptionResult = decryptWithOlmSession(decrypted)
                            newStoredOlmSession = StoredOlmSession(
                                sessionId = olmSession.sessionId,
                                senderKey = senderIdentityKey,
                                pickled = olmSession.pickle(store.getOlmPickleKey()),
                                lastUsedAt = Clock.System.now()
                            )
                            olmAccount.pickle(store.getOlmPickleKey())
                        }
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    createRecoveryOlmSession(storedSessions)
                    throw DecryptOlmError.OlmLibraryError(olmLibraryException)
                }
                newStoredOlmSession
            } else {
                createRecoveryOlmSession(storedSessions)
                throw DecryptOlmError.NoMatchingOlmSessionFound
            }
        }
        decryptionResult
            .also { log.trace { "decrypted event: $it" } }
    }.onFailure { log.warn(it) { "decrypt olm failed" } }

    private fun hasCreatedTooManyOlmSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = Clock.System.now()
        return (storedSessions?.size ?: 0) >= 3 && storedSessions
            ?.sortedByDescending { it.createdAt }
            ?.takeLast(3)
            ?.map { it.createdAt.plus(1, DateTimeUnit.HOUR) <= now }
            ?.all { true } == true
    }

    private fun Set<StoredOlmSession>?.addOrUpdateNewAndRemoveOldSessions(newSession: StoredOlmSession): Set<StoredOlmSession> {
        val newSessions = this?.filterNot { it.sessionId == newSession.sessionId }?.toSet().orEmpty() + newSession
        return if (newSessions.size > 9) {
            newSessions.sortedBy { it.lastUsedAt }.drop(1).toSet()
        } else newSessions
    }

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): Result<MegolmEncryptedMessageEventContent> = runCatching {
        val rotationPeriodMs = settings.rotationPeriodMs
        val rotationPeriodMsgs = settings.rotationPeriodMsgs

        lateinit var finalEncryptionResult: MegolmEncryptedMessageEventContent
        store.updateOutboundMegolmSession(roomId) { storedSession ->

            @OptIn(ExperimentalSerializationApi::class)
            suspend fun encryptWithMegolmSession(
                session: OlmOutboundGroupSession,
                content: MessageEventContent,
                roomId: RoomId,
                newUserDevices: Map<UserId, Set<String>>
            ): MegolmEncryptedMessageEventContent {
                val newUserDevicesWithoutUs = newUserDevices
                    .mapValues { (userId, deviceIds) -> if (userId == ownUserId) deviceIds - ownDeviceId else deviceIds }
                    .filterValues { it.isNotEmpty() }
                if (newUserDevicesWithoutUs.isNotEmpty()) {
                    val roomKeyEventContent = RoomKeyEventContent(
                        roomId = roomId,
                        sessionId = session.sessionId,
                        sessionKey = session.sessionKey,
                        algorithm = Megolm
                    )

                    log.debug { "send megolm key to devices: $newUserDevicesWithoutUs" }
                    val eventsToSend = newUserDevicesWithoutUs.mapNotNull { (user, devices) ->
                        val deviceEvents = devices.filterNot { user == ownUserId && it == ownDeviceId }
                            .mapNotNull { deviceId ->
                                encryptOlm(roomKeyEventContent, user, deviceId)
                                    .fold(
                                        onSuccess = { deviceId to it },
                                        onFailure = {
                                            log.warn(it) { "could not encrypt room key with olm for (user=$user, deviceId=$deviceId)" }
                                            null
                                        }
                                    )
                            }.toMap()
                        if (deviceEvents.isEmpty()) null
                        else user to deviceEvents
                    }.toMap()
                    if (eventsToSend.isNotEmpty()) requests.sendToDevice(eventsToSend).getOrThrow()
                }

                val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
                val event = DecryptedMegolmEvent(content, roomId).also { log.trace { "megolm event: $it" } }
                checkNotNull(serializer)

                val encryptedContent = session.encrypt(json.encodeToString(serializer, event))

                return MegolmEncryptedMessageEventContent(
                    ciphertext = encryptedContent,
                    senderKey = ownCurve25519Key,
                    deviceId = ownDeviceId,
                    sessionId = session.sessionId,
                    relatesTo = relatesToForEncryptedEvent(content)
                ).also { log.trace { "encrypted event: $it" } }
            }

            val (encryptionResult, pickledSession) = if (
                storedSession == null
                || rotationPeriodMs != null && (storedSession
                    .createdAt.plus(rotationPeriodMs, MILLISECOND) <= Clock.System.now())
                || rotationPeriodMsgs != null && (storedSession.encryptedMessageCount >= rotationPeriodMsgs)
            ) {
                log.debug { "encrypt megolm event with new session" }
                val newUserDevices =
                    store.getDevices(roomId, store.getHistoryVisibility(roomId).membershipsAllowedToReceiveKey)
                        .orEmpty()
                try {
                    freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
                        freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                            store.updateInboundMegolmSession(inboundSession.sessionId, roomId) {
                                StoredInboundMegolmSession(
                                    senderKey = ownCurve25519Key,
                                    sessionId = inboundSession.sessionId,
                                    roomId = roomId,
                                    firstKnownIndex = inboundSession.firstKnownIndex,
                                    hasBeenBackedUp = false,
                                    isTrusted = true,
                                    senderSigningKey = ownEd25519Key,
                                    forwardingCurve25519KeyChain = listOf(),
                                    pickled = inboundSession.pickle(store.getOlmPickleKey()),
                                )
                            }
                        }
                        encryptWithMegolmSession(outboundSession, content, roomId, newUserDevices) to
                                outboundSession.pickle(store.getOlmPickleKey())
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    throw EncryptMegolmError.OlmLibraryError(olmLibraryException)
                }
            } else {
                log.debug { "encrypt megolm event with existing session" }
                try {
                    freeAfter(
                        OlmOutboundGroupSession.unpickle(
                            store.getOlmPickleKey(),
                            storedSession.pickled
                        )
                    ) { session ->
                        encryptWithMegolmSession(session, content, roomId, storedSession.newDevices) to
                                session.pickle(store.getOlmPickleKey())
                    }
                } catch (olmLibraryException: OlmLibraryException) {
                    throw EncryptMegolmError.OlmLibraryError(olmLibraryException)
                }
            }
            finalEncryptionResult = encryptionResult
            storedSession?.copy(
                encryptedMessageCount = storedSession.encryptedMessageCount + 1,
                pickled = pickledSession,
                newDevices = emptyMap(),
            ) ?: StoredOutboundMegolmSession(
                roomId = roomId,
                pickled = pickledSession,
            )
        }
        finalEncryptionResult
    }.onFailure { log.warn(it) { "encrypt megolm failed" } }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptMegolm(
        encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>
    ): Result<DecryptedMegolmEvent<*>> = runCatching {
        val roomId = encryptedEvent.roomId
        val encryptedContent = encryptedEvent.content
        val sessionId = encryptedContent.sessionId

        val storedSession = store.getInboundMegolmSession(sessionId, roomId)
            ?: throw DecryptMegolmError.MegolmKeyNotFound

        val decryptionResult = try {
            freeAfter(OlmInboundGroupSession.unpickle(store.getOlmPickleKey(), storedSession.pickled)) { session ->
                session.decrypt(encryptedContent.ciphertext)
            }
        } catch (e: OlmLibraryException) {
            if (e.message?.contains("UNKNOWN_MESSAGE_INDEX") == true)
                throw DecryptMegolmError.MegolmKeyUnknownMessageIndex
            else throw DecryptMegolmError.OlmLibraryError(e)
        }

        val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
        checkNotNull(serializer)
        val decryptedEvent =
            try {
                json.decodeFromJsonElement(
                    serializer,
                    addRelatesToToDecryptedEvent(decryptionResult.message, encryptedContent.relatesTo)
                )
            } catch (e: SerializationException) {
                throw DecryptMegolmError.DeserializationError(e)
            }
        val index = decryptionResult.index
        store.updateInboundMegolmMessageIndex(sessionId, roomId, index) { storedIndex ->
            if (encryptedEvent.roomId != decryptedEvent.roomId)
                throw DecryptMegolmError.ValidationFailed("roomId did not match")
            if (storedIndex != null
                && (storedIndex.eventId != encryptedEvent.id || storedIndex.originTimestamp != encryptedEvent.originTimestamp)
            ) throw DecryptMegolmError.ValidationFailed("message index did not match")

            storedIndex ?: StoredInboundMegolmMessageIndex(
                sessionId, roomId, index, encryptedEvent.id, encryptedEvent.originTimestamp
            )
        }

        decryptedEvent.also { log.trace { "decrypted event: $it" } }
    }.onFailure { log.warn(it) { "decrypt megolm failed" } }

    private fun addRelatesToToDecryptedEvent(
        decryptionJson: String,
        relatesTo: RelatesTo?
    ) = JsonObject(buildMap {
        val originalJsonObject = json.decodeFromString<JsonObject>(decryptionJson).jsonObject
        putAll(originalJsonObject)
        relatesTo?.let { relatesTo ->
            originalJsonObject["content"]?.jsonObject?.let { content ->
                put("content", JsonObject(buildMap {
                    putAll(content)
                    put("m.relates_to", json.encodeToJsonElement(relatesTo))
                }
                ))
            }
        }
    })

    private fun relatesToForEncryptedEvent(content: EventContent) =
        if (content is MessageEventContent) {
            val relatesTo = content.relatesTo
            if (relatesTo is RelatesTo.Replace) relatesTo.copy(newContent = null)
            else relatesTo
        } else null
}

