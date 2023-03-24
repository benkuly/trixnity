package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.Companion.MILLISECOND
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import mu.KotlinLogging
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.*
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.crypto.sign.verify
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.ORDINARY

private val log = KotlinLogging.logger {}

interface OlmEncryptionService {
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

    suspend fun decryptMegolm(encryptedEvent: RoomEvent<MegolmEncryptedEventContent>): DecryptedMegolmEvent<*>
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

    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): OlmEncryptedEventContent {
        val identityKey = store.findCurve25519Key(receiverId, deviceId)
            ?: throw KeyException.KeyNotFoundException("could not find curve25519 key for $receiverId ($deviceId)")
        val signingKey = store.findEd25519Key(receiverId, deviceId)
            ?: throw KeyException.KeyNotFoundException("could not find ed25519 key for $receiverId ($deviceId)")

        lateinit var finalEncryptionResult: OlmEncryptedEventContent
        store.updateOlmSessions(identityKey) { storedOlmSessions ->
            val storedSession = storedOlmSessions?.minByOrNull { it.sessionId }

            val (encryptionResult, newStoredSession) = if (storedSession == null || forceNewSession) {
                log.debug { "encrypt olm event with new session for device with key $identityKey" }
                val response =
                    requests.claimKeys(mapOf(receiverId to mapOf(deviceId to KeyAlgorithm.SignedCurve25519)))
                        .getOrThrow()
                if (response.failures.isNotEmpty()) throw KeyException.CouldNotReachRemoteServersException(response.failures.keys)
                val oneTimeKey = response.oneTimeKeys[receiverId]?.get(deviceId)?.keys?.firstOrNull()
                    ?: throw KeyException.OneTimeKeyNotFoundException(receiverId, deviceId)
                require(oneTimeKey is SignedCurve25519Key)
                val keyVerifyState = signService.verify(oneTimeKey, mapOf(receiverId to setOf(signingKey)))
                if (keyVerifyState is VerifyResult.Invalid)
                    throw KeyException.KeyVerificationFailedException(keyVerifyState.reason)
                val olmAccount = OlmAccount.unpickle(store.olmPickleKey, requireNotNull(store.olmAccount.value))
                freeAfter(
                    olmAccount,
                    OlmSession.createOutbound(
                        account = olmAccount,
                        theirIdentityKey = identityKey.value,
                        theirOneTimeKey = oneTimeKey.signed.value
                    )
                ) { _, session ->
                    encryptWithOlmSession(
                        session,
                        content,
                        receiverId,
                        deviceId,
                        identityKey
                    ) to StoredOlmSession(
                        sessionId = session.sessionId,
                        senderKey = identityKey,
                        pickled = session.pickle(store.olmPickleKey),
                        lastUsedAt = Clock.System.now()
                    )
                }
            } else {
                log.debug { "encrypt olm event with existing session for device with key $identityKey" }
                freeAfter(OlmSession.unpickle(store.olmPickleKey, storedSession.pickled)) { session ->
                    encryptWithOlmSession(session, content, receiverId, deviceId, identityKey) to StoredOlmSession(
                        sessionId = session.sessionId,
                        senderKey = identityKey,
                        pickled = session.pickle(store.olmPickleKey),
                        lastUsedAt = Clock.System.now()
                    )
                }
            }
            finalEncryptionResult = encryptionResult
            storedOlmSessions.addOrUpdateNewAndRemoveOldSessions(newStoredSession)
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
                store.findEd25519Key(receiverId, deviceId)?.copy(keyId = null)
                    ?: throw KeyException.KeyNotFoundException("could not find es25519 key for $receiverId ($deviceId)")
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
            relatesTo = relatesToForEncryptedEvent(content)
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
            ?: throw DecryptionException.SenderDidNotEncryptForThisDeviceException
        val senderIdentityKey = encryptedContent.senderKey
        val senderDeviceKeys = store.findDeviceKeys(senderId, senderIdentityKey)
            ?: throw KeyException.KeyVerificationFailedException("the sender key of the event is not known")
        val senderSigningKey = senderDeviceKeys.keys.keys.filterIsInstance<Ed25519Key>().firstOrNull()
            ?: throw KeyException.KeyVerificationFailedException("we do not know any signing key of the sender")

        lateinit var finalDecryptionResult: DecryptedOlmEvent<*>
        store.updateOlmSessions(senderIdentityKey) { storedSessions ->
            val (decryptionResult, newStoredSession) = try {
                storedSessions?.sortedByDescending { it.lastUsedAt }?.firstNotNullOfOrNull { storedSession ->
                    freeAfter(OlmSession.unpickle(store.olmPickleKey, storedSession.pickled)) { olmSession ->
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
                                pickled = olmSession.pickle(store.olmPickleKey),
                                lastUsedAt = Clock.System.now()
                            )
                        }
                    }
                } ?: if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                    if (hasCreatedTooManyOlmSessions(storedSessions).not()) {
                        log.debug { "decrypt olm event with new session for device with key $senderIdentityKey" }
                        lateinit var decryptedPair: Pair<String, StoredOlmSession>
                        store.olmAccount.update {
                            val olmAccount = OlmAccount.unpickle(
                                store.olmPickleKey, requireNotNull(it) { "pickled olm account was null" })
                            freeAfter(
                                olmAccount,
                                OlmSession.createInboundFrom(olmAccount, senderIdentityKey.value, ciphertext.body)
                            ) { _, olmSession ->
                                val decrypted = olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                                olmAccount.removeOneTimeKeys(olmSession)
                                decryptedPair = decrypted to StoredOlmSession(
                                    sessionId = olmSession.sessionId,
                                    senderKey = senderIdentityKey,
                                    pickled = olmSession.pickle(store.olmPickleKey),
                                    lastUsedAt = Clock.System.now()
                                )
                                olmAccount.pickle(store.olmPickleKey)
                            }
                        }
                        decryptedPair
                    } else throw DecryptionException.PreventToManySessions
                } else {
                    throw DecryptionException.CouldNotDecrypt
                }
            } catch (decryptError: Throwable) {
                if (hasCreatedTooManyOlmSessions(storedSessions).not()) {
                    val deviceId = senderDeviceKeys.deviceId
                    val dummyEvent = try {
                        encryptOlm(DummyEventContent, senderId, deviceId, true)
                    } catch (e: Exception) {
                        if (e is KeyException.KeyNotFoundException) {
                            log.trace { "do not recover corrupted olm session because device does not exist anymore" }
                        } else log.warn(e) { "could not encrypt dummy event for $senderId ($deviceId)" }
                        null
                    }
                    if (dummyEvent != null) {
                        log.info { "try recover corrupted olm session by sending a dummy event due to: ${decryptError.message}" }
                        requests.sendToDevice(mapOf(senderId to mapOf(deviceId to dummyEvent))).getOrThrow()
                    }
                }
                throw decryptError
            }

            val serializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
            requireNotNull(serializer)
            val decryptedEvent =
                json.decodeFromJsonElement(
                    serializer,
                    addRelatesToToDecryptedEvent(decryptionResult, encryptedContent.relatesTo)
                )


            if (decryptedEvent.sender != senderId) throw DecryptionException.ValidationFailed("sender did not match")
            if (decryptedEvent.recipient != ownUserId) throw DecryptionException.ValidationFailed("recipient did not match")
            if (decryptedEvent.recipientKeys.filterIsInstance<Ed25519Key>()
                    .firstOrNull()?.value != ownEd25519Key.value
            ) throw DecryptionException.ValidationFailed("recipientKeys did not match")
            if (decryptedEvent.senderKeys.filterIsInstance<Ed25519Key>()
                    .firstOrNull()?.value != senderSigningKey.value
            ) throw DecryptionException.ValidationFailed("senderKeys did not match")
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
        store.updateOutboundMegolmSession(roomId) { storedSession ->
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
                                pickled = inboundSession.pickle(store.olmPickleKey),
                            )
                        }
                    }
                    encryptWithMegolmSession(outboundSession, content, roomId, newUserDevices) to
                            outboundSession.pickle(store.olmPickleKey)
                }
            } else {
                log.debug { "encrypt megolm event with existing session" }
                freeAfter(OlmOutboundGroupSession.unpickle(store.olmPickleKey, storedSession.pickled)) { session ->
                    encryptWithMegolmSession(session, content, roomId, storedSession.newDevices) to
                            session.pickle(store.olmPickleKey)
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
                        } catch (e: Exception) {
                            log.warn(e) { "could not encrypt room key with olm for $user ($deviceId)" }
                            null
                        }
                    }.toMap()
                if (deviceEvents.isEmpty()) null
                else user to deviceEvents
            }.toMap()
            if (eventsToSend.isNotEmpty()) requests.sendToDevice(eventsToSend).getOrThrow()
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
            relatesTo = relatesToForEncryptedEvent(content)
        ).also { log.trace { "encrypted event: $it" } }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun decryptMegolm(encryptedEvent: RoomEvent<MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        val roomId = encryptedEvent.roomId
        val encryptedContent = encryptedEvent.content
        val sessionId = encryptedContent.sessionId

        val storedSession = store.getInboundMegolmSession(sessionId, roomId)
            ?: throw DecryptionException.SenderDidNotSendMegolmKeysToUs

        val decryptionResult = try {
            freeAfter(OlmInboundGroupSession.unpickle(store.olmPickleKey, storedSession.pickled)) { session ->
                session.decrypt(encryptedContent.ciphertext)
            }
        } catch (e: OlmLibraryException) {
            throw DecryptionException.SessionException(e)
        }

        val serializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
        requireNotNull(serializer)
        val decryptedEvent =
            json.decodeFromJsonElement(
                serializer,
                addRelatesToToDecryptedEvent(decryptionResult.message, encryptedContent.relatesTo)
            )
        val index = decryptionResult.index
        store.updateInboundMegolmMessageIndex(sessionId, roomId, index) { storedIndex ->
            if (encryptedEvent.roomId != decryptedEvent.roomId) throw DecryptionException.ValidationFailed("roomId did not match")
            if (storedIndex?.let { it.eventId != encryptedEvent.id || it.originTimestamp != encryptedEvent.originTimestamp } == true
            ) throw DecryptionException.ValidationFailed("message index did not match")

            storedIndex ?: StoredInboundMegolmMessageIndex(
                sessionId, roomId, index, encryptedEvent.id, encryptedEvent.originTimestamp
            )
        }

        return decryptedEvent.also { log.trace { "decrypted event: $it" } }
    }

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

    private fun hasCreatedTooManyOlmSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = Clock.System.now()
        return (storedSessions?.size ?: 0) >= 3 && storedSessions
            ?.sortedByDescending { it.createdAt }
            ?.takeLast(3)
            ?.map { it.createdAt.plus(1, DateTimeUnit.HOUR) <= now }
            ?.all { true } == true
    }
}