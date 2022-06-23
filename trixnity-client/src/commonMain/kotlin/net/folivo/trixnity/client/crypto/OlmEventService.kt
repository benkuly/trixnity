package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.Companion.MILLISECOND
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.KeyException.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.*
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.ORDINARY
import kotlin.coroutines.cancellation.CancellationException

private val log = KotlinLogging.logger {}

interface IOlmEventService {
    val decryptedOlmEvents: SharedFlow<IOlmService.DecryptedOlmEventContainer>

    suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean = false
    ): OlmEncryptedEventContent

    suspend fun decryptOlm(encryptedContent: OlmEncryptedEventContent, senderId: UserId): DecryptedOlmEvent<*>

    suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): MegolmEncryptedEventContent

    suspend fun decryptMegolm(encryptedEvent: MessageEvent<MegolmEncryptedEventContent>): DecryptedMegolmEvent<*>
}

class OlmEventService internal constructor(
    private val olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val ownEd25519Key: Ed25519Key,
    private val ownCurve25519Key: Curve25519Key,
    private val json: Json,
    private val olmAccount: OlmAccount,
    private val store: Store,
    private val api: MatrixClientServerApiClient,
    private val signService: IOlmSignService,
) : IOlmEventService {

    init {
        api.sync.subscribe(::handleOlmEncryptedToDeviceEvents)
    }

    private val _decryptedOlmEvents = MutableSharedFlow<IOlmService.DecryptedOlmEventContainer>()
    override val decryptedOlmEvents = _decryptedOlmEvents.asSharedFlow()

    internal suspend fun handleOlmEncryptedToDeviceEvents(event: ClientEvent<OlmEncryptedEventContent>) {
        if (event is ClientEvent.ToDeviceEvent) {
            try {
                val decryptedEvent = decryptOlm(event.content, event.sender)
                _decryptedOlmEvents.emit(IOlmService.DecryptedOlmEventContainer(event, decryptedEvent))
            } catch (e: Exception) {
                log.error(e) { "could not decrypt $event" }
                if (e is CancellationException) throw e
            }
        }
    }

    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): OlmEncryptedEventContent {
        val identityKey = store.keys.getOrFetchKeyFromDevice<Curve25519Key>(receiverId, deviceId)
            ?: throw KeyNotFoundException("could not find curve25519 key for $receiverId ($deviceId)")
        val signingKey = store.keys.getDeviceKey(receiverId, deviceId)?.value?.get<Ed25519Key>()
            ?: throw KeyNotFoundException("could not find ed25519 key for $receiverId ($deviceId)")

        lateinit var finalEncryptionResult: OlmEncryptedEventContent
        store.olm.updateOlmSessions(identityKey) { storedSessions ->
            val storedSession = storedSessions?.minByOrNull { it.sessionId }

            val (encryptionResult, newStoredSession) = if (storedSession == null || forceNewSession) {
                log.debug { "encrypt olm event with new session for device with key $identityKey" }
                val response =
                    api.keys.claimKeys(mapOf(receiverId to mapOf(deviceId to KeyAlgorithm.SignedCurve25519)))
                        .getOrThrow()
                if (response.failures.isNotEmpty()) throw CouldNotReachRemoteServersException(response.failures.keys)
                val oneTimeKey = response.oneTimeKeys[receiverId]?.get(deviceId)?.keys?.firstOrNull()
                    ?: throw OneTimeKeyNotFoundException(receiverId, deviceId)
                require(oneTimeKey is SignedCurve25519Key)
                val keyVerifyState = signService.verify(oneTimeKey, mapOf(receiverId to setOf(signingKey)))
                if (keyVerifyState is VerifyResult.Invalid)
                    throw KeyVerificationFailedException(keyVerifyState.reason)
                freeAfter(
                    OlmSession.createOutbound(
                        account = olmAccount,
                        theirIdentityKey = identityKey.value,
                        theirOneTimeKey = oneTimeKey.signed.value
                    )
                ) { session ->
                    encryptWithOlmSession(session, content, receiverId, deviceId, identityKey) to StoredOlmSession(
                        sessionId = session.sessionId,
                        senderKey = identityKey,
                        pickled = session.pickle(olmPickleKey),
                        lastUsedAt = Clock.System.now()
                    )
                }
            } else {
                log.debug { "encrypt olm event with existing session for device with key $identityKey" }
                freeAfter(OlmSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                    encryptWithOlmSession(session, content, receiverId, deviceId, identityKey) to StoredOlmSession(
                        sessionId = session.sessionId,
                        senderKey = identityKey,
                        pickled = session.pickle(olmPickleKey),
                        lastUsedAt = Clock.System.now()
                    )
                }
            }
            finalEncryptionResult = encryptionResult
            storedSessions.addOrUpdateNewAndRemoveOldSessions(newStoredSession)
        }
        return finalEncryptionResult
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun encryptWithOlmSession(
        olmSession: OlmSession,
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        identityKey: Curve25519Key
    ): OlmEncryptedEventContent {
        val serializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
        val event = DecryptedOlmEvent(
            content = content,
            sender = ownUserId,
            senderKeys = keysOf(ownEd25519Key.copy(keyId = null)),
            recipient = receiverId,
            recipientKeys = keysOf(
                store.keys.getOrFetchKeyFromDevice<Ed25519Key>(receiverId, deviceId)?.copy(keyId = null)
                    ?: throw KeyNotFoundException("could not find es25519 key for $receiverId ($deviceId)")
            )
        ).also { log.trace { "olm event: $it" } }
        requireNotNull(serializer)
        val encryptedContent = olmSession.encrypt(json.encodeToString(serializer, event))
        return OlmEncryptedEventContent(
            ciphertext = mapOf(
                identityKey.value to CiphertextInfo(
                    encryptedContent.cipherText,
                    OlmMessageType.of(encryptedContent.type.value)
                )
            ),
            senderKey = ownCurve25519Key,
            relatesTo = if (content is MessageEventContent) content.relatesTo else null
        ).also { log.trace { "encrypted event: $it" } }
    }

    private fun Set<StoredOlmSession>?.addOrUpdateNewAndRemoveOldSessions(newSession: StoredOlmSession): Set<StoredOlmSession> {
        val newSessions =
            (this?.filterNot { it.sessionId == newSession.sessionId }?.toSet() ?: setOf()) + newSession
        return if (newSessions.size > 9) {
            newSessions.sortedBy { it.lastUsedAt }.drop(1).toSet()
        } else newSessions
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptOlm(
        encryptedContent: OlmEncryptedEventContent,
        senderId: UserId
    ): DecryptedOlmEvent<*> {
        log.debug { "start decrypt olm event $encryptedContent" }
        val ciphertext = encryptedContent.ciphertext[ownCurve25519Key.value]
            ?: throw SessionException.SenderDidNotEncryptForThisDeviceException
        val senderIdentityKey =
            store.keys.getDeviceKeyByValue<Curve25519Key>(senderId, encryptedContent.senderKey.value)
                ?: throw KeyVerificationFailedException("the sender key of the event is not known for this device")

        lateinit var finalDecryptionResult: DecryptedOlmEvent<*>
        store.olm.updateOlmSessions(senderIdentityKey) { storedSessions ->
            val (decryptionResult, newStoredSession) = try {
                storedSessions?.sortedByDescending { it.lastUsedAt }?.firstNotNullOfOrNull { storedSession ->
                    freeAfter(OlmSession.unpickle(olmPickleKey, storedSession.pickled)) { olmSession ->
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
                                log.warn { "could not decrypt olm event with existing session ${storedSession.sessionId} for device with key $senderIdentityKey. Reason: ${error.message}" }
                                null
                            }
                        }?.let {
                            it to StoredOlmSession(
                                sessionId = olmSession.sessionId,
                                senderKey = senderIdentityKey,
                                pickled = olmSession.pickle(olmPickleKey),
                                lastUsedAt = Clock.System.now()
                            )
                        }
                    }
                } ?: if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                    if (hasCreatedTooManyOlmSessions(storedSessions).not()) {
                        log.debug { "decrypt olm event with new session for device with key $senderIdentityKey" }
                        freeAfter(
                            OlmSession.createInboundFrom(olmAccount, senderIdentityKey.value, ciphertext.body)
                        ) { olmSession ->
                            val decrypted = olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                            olmAccount.removeOneTimeKeys(olmSession)
                            store.olm.storeAccount(olmAccount, olmPickleKey)
                            decrypted to StoredOlmSession(
                                sessionId = olmSession.sessionId,
                                senderKey = senderIdentityKey,
                                pickled = olmSession.pickle(olmPickleKey),
                                lastUsedAt = Clock.System.now()
                            )
                        }
                    } else throw SessionException.PreventToManySessions
                } else {
                    throw SessionException.CouldNotDecrypt
                }
            } catch (decryptError: Throwable) {
                if (hasCreatedTooManyOlmSessions(storedSessions).not()) {
                    val senderDeviceId =
                        store.keys.getDeviceKeys(senderId)?.entries
                            ?.find { it.value.value.signed.keys.contains(senderIdentityKey) }?.key
                            ?: throw KeyVerificationFailedException("the sender key of the event is not known for this device")
                    try {
                        log.debug { "try recover corrupted olm session by sending a dummy event" }
                        api.users.sendToDevice(
                            mapOf(
                                senderId to mapOf(
                                    senderDeviceId to encryptOlm(
                                        DummyEventContent,
                                        senderId,
                                        senderDeviceId,
                                        true
                                    )
                                )
                            )
                        ).getOrThrow()
                    } catch (sendError: Throwable) {
                        log.warn(sendError) { "could not send m.dummy to $senderId ($senderDeviceId)" }
                    }
                }
                throw decryptError
            }

            val serializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
            requireNotNull(serializer)
            val decryptedEvent =
                json.decodeFromJsonElement(serializer, addRelatesTo(decryptionResult, encryptedContent.relatesTo))

            if (decryptedEvent.sender != senderId
                || decryptedEvent.recipient != ownUserId
                || decryptedEvent.recipientKeys.get<Ed25519Key>()?.value != ownEd25519Key.value
                || decryptedEvent.senderKeys.get<Ed25519Key>()?.value?.let {
                    store.keys.getDeviceKeyByValue<Ed25519Key>(senderId, it)
                } == null
            ) throw SessionException.ValidationFailed

            finalDecryptionResult = decryptedEvent
            storedSessions.addOrUpdateNewAndRemoveOldSessions(newStoredSession)
        }

        return finalDecryptionResult.also { log.trace { "decrypted event: $it" } }
    }

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): MegolmEncryptedEventContent {
        val rotationPeriodMs = settings.rotationPeriodMs
        val rotationPeriodMsgs = settings.rotationPeriodMsgs

        lateinit var finalEncryptionResult: MegolmEncryptedEventContent
        store.olm.updateOutboundMegolmSession(roomId) { storedSession ->
            val (encryptionResult, pickledSession) = if (
                storedSession == null
                || rotationPeriodMs != null && (storedSession
                    .createdAt.plus(rotationPeriodMs, MILLISECOND) <= Clock.System.now())
                || rotationPeriodMsgs != null && (storedSession.encryptedMessageCount >= rotationPeriodMsgs)
            ) {
                store.room.get(roomId).first { it?.membersLoaded == true }
                val members = store.roomState.members(roomId, JOIN, INVITE)
                store.keys.waitForUpdateOutdatedKey(members)
                log.debug { "encrypt megolm event with new session" }
                val newUserDevices =
                    members.mapNotNull { userId ->
                        store.keys.getDeviceKeys(userId)?.let { userId to it.keys }
                    }.toMap()
                freeAfter(OlmOutboundGroupSession.create()) { session ->
                    store.olm.storeTrustedInboundMegolmSession(
                        roomId = roomId,
                        senderKey = ownCurve25519Key,
                        senderSigningKey = ownEd25519Key,
                        sessionId = session.sessionId,
                        sessionKey = session.sessionKey,
                        pickleKey = olmPickleKey
                    )
                    encryptWithMegolmSession(session, content, roomId, newUserDevices) to session.pickle(olmPickleKey)
                }
            } else {
                log.debug { "encrypt megolm event with existing session" }
                freeAfter(OlmOutboundGroupSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                    encryptWithMegolmSession(session, content, roomId, storedSession.newDevices) to session.pickle(
                        olmPickleKey
                    )
                }
            }
            finalEncryptionResult = encryptionResult
            storedSession?.copy(
                encryptedMessageCount = storedSession.encryptedMessageCount + 1,
                pickled = pickledSession,
                newDevices = emptyMap()
            ) ?: StoredOutboundMegolmSession(
                roomId = roomId,
                pickled = pickledSession,
            )
        }
        return finalEncryptionResult
    }

    @OptIn(ExperimentalSerializationApi::class)

    private suspend fun encryptWithMegolmSession(
        session: OlmOutboundGroupSession,
        content: MessageEventContent,
        roomId: RoomId,
        newUserDevices: Map<UserId, Set<String>>
    ): MegolmEncryptedEventContent {
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
                        try {
                            deviceId to encryptOlm(roomKeyEventContent, user, deviceId)
                        } catch (e: Throwable) {
                            log.warn(e) { "could not encrypt room key with olm for $user ($deviceId)" }
                            null
                        }
                    }.toMap()
                if (deviceEvents.isEmpty()) null
                else user to deviceEvents
            }.toMap()
            if (eventsToSend.isNotEmpty()) api.users.sendToDevice(eventsToSend).getOrThrow()
        }

        val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
        val event = DecryptedMegolmEvent(content, roomId).also { log.trace { "megolm event: $it" } }
        requireNotNull(serializer)

        val encryptedContent = session.encrypt(json.encodeToString(serializer, event))

        return MegolmEncryptedEventContent(
            ciphertext = encryptedContent,
            senderKey = ownCurve25519Key,
            deviceId = ownDeviceId,
            sessionId = session.sessionId,
            relatesTo = content.relatesTo
        ).also { log.trace { "encrypted event: $it" } }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptMegolm(encryptedEvent: MessageEvent<MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        val roomId = encryptedEvent.roomId
        val encryptedContent = encryptedEvent.content
        val sessionId = encryptedContent.sessionId

        val storedSession = store.olm.getInboundMegolmSession(sessionId, roomId)
            ?: throw DecryptionException.SenderDidNotSendMegolmKeysToUs

        val decryptionResult = try {
            freeAfter(OlmInboundGroupSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                session.decrypt(encryptedContent.ciphertext)
            }
        } catch (e: OlmLibraryException) {
            throw DecryptionException.SessionException(e)
        }

        val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
        requireNotNull(serializer)
        val decryptedEvent =
            json.decodeFromJsonElement(serializer, addRelatesTo(decryptionResult.message, encryptedContent.relatesTo))
        val index = decryptionResult.index
        store.olm.updateInboundMegolmMessageIndex(sessionId, roomId, index) { storedIndex ->
            if (encryptedEvent.roomId != decryptedEvent.roomId
                || storedIndex?.let { it.eventId != encryptedEvent.id || it.originTimestamp != encryptedEvent.originTimestamp } == true
            ) throw DecryptionException.ValidationFailed

            storedIndex ?: StoredInboundMegolmMessageIndex(
                sessionId, roomId, index, encryptedEvent.id, encryptedEvent.originTimestamp
            )
        }

        return decryptedEvent.also { log.trace { "decrypted event: $it" } }
    }

    private fun addRelatesTo(
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

    private fun hasCreatedTooManyOlmSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = Clock.System.now()
        return (storedSessions?.size ?: 0) >= 3 && storedSessions
            ?.sortedByDescending { it.createdAt }
            ?.takeLast(3)
            ?.map { it.createdAt.plus(1, DateTimeUnit.HOUR) <= now }
            ?.all { true } == true
    }
}