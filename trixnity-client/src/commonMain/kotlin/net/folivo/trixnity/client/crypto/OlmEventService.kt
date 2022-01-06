package net.folivo.trixnity.client.crypto

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.Companion.MILLISECOND
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.KeyException.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.Key.*
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm
import net.folivo.trixnity.core.model.crypto.keysOf
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType.ORDINARY

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalSerializationApi::class)
class OlmEventService internal constructor(
    private val olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val json: Json,
    private val account: OlmAccount,
    private val store: Store,
    private val api: MatrixApiClient,
    private val signService: OlmSignService,
) {
    private val ownEd25519Key = Ed25519Key(ownDeviceId, account.identityKeys.ed25519)
    private val ownCurve25519Key = Curve25519Key(ownDeviceId, account.identityKeys.curve25519)

    suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String
    ): OlmEncryptedEventContent {
        val identityKey = store.keys.getOrFetchKeyFromDevice<Curve25519Key>(receiverId, deviceId)
            ?: throw KeyNotFoundException("could not find curve25519 key for $receiverId ($deviceId)")
        val storedSession = store.olm.getOlmSessions(identityKey)?.minByOrNull { it.sessionId }

        return if (storedSession == null) {
            log.debug { "encrypt olm event with new session for device with key $identityKey" }
            val response =
                api.keys.claimKeys(mapOf(receiverId to mapOf(deviceId to KeyAlgorithm.SignedCurve25519))).getOrThrow()
            if (response.failures.isNotEmpty()) throw CouldNotReachRemoteServersException(response.failures.keys)
            val oneTimeKey = response.oneTimeKeys[receiverId]?.get(deviceId)?.keys?.firstOrNull()
                ?: throw OneTimeKeyNotFoundException(receiverId, deviceId)
            require(oneTimeKey is SignedCurve25519Key)
            val keyVerifyState = signService.verify(oneTimeKey)
            if (keyVerifyState is VerifyResult.Invalid)
                throw KeyVerificationFailedException(keyVerifyState.reason)
            freeAfter(
                OlmSession.createOutbound(
                    account = account,
                    theirIdentityKey = identityKey.value,
                    theirOneTimeKey = oneTimeKey.signed.value
                )
            ) { session ->
                encryptWithOlmSession(session, content, receiverId, deviceId, identityKey).also {
                    store.olm.storeOlmSession(session, identityKey, olmPickleKey)
                }
            }
        } else {
            log.debug { "encrypt olm event with existing session for device with key $identityKey" }
            freeAfter(OlmSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                encryptWithOlmSession(session, content, receiverId, deviceId, identityKey).also {
                    store.olm.storeOlmSession(session, identityKey, olmPickleKey)
                }
            }
        }
    }

    private suspend fun encryptWithOlmSession(
        olmSession: OlmSession,
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        identityKey: Curve25519Key
    ): OlmEncryptedEventContent {
        val serializer = json.serializersModule.getContextual(OlmEvent::class)
        val event = OlmEvent(
            content = content,
            sender = ownUserId,
            senderKeys = keysOf(ownEd25519Key.copy(keyId = null)),
            recipient = receiverId,
            recipientKeys = keysOf(
                store.keys.getOrFetchKeyFromDevice<Ed25519Key>(receiverId, deviceId)?.copy(keyId = null)
                    ?: throw KeyNotFoundException("could not find es25519 key for $receiverId ($deviceId)")
            )
        ).also { log.debug { "olm event: $it" } }
        requireNotNull(serializer)
        val encryptedContent = olmSession.encrypt(json.encodeToString(serializer, event))
        store.olm.storeOlmSession(olmSession, identityKey, olmPickleKey)
        return OlmEncryptedEventContent(
            ciphertext = mapOf(
                identityKey.value to CiphertextInfo(
                    encryptedContent.cipherText,
                    OlmMessageType.of(encryptedContent.type.value)
                )
            ),
            senderKey = ownCurve25519Key
        ).also { log.debug { "encrypted event: $it" } }
    }

    suspend fun decryptOlm(encryptedContent: OlmEncryptedEventContent, senderId: UserId): OlmEvent<*> {
        log.debug { "start decrypt olm event $encryptedContent" }
        val ciphertext = encryptedContent.ciphertext[ownCurve25519Key.value]
            ?: throw SessionException.SenderDidNotEncryptForThisDeviceException
        val senderIdentityKey =
            store.keys.getDeviceKeyByValue<Curve25519Key>(senderId, encryptedContent.senderKey.value)
                ?: throw KeyVerificationFailedException("the sender key of the event is not known for this device")
        val storedSessions = store.olm.getOlmSessions(senderIdentityKey)
        val decryptedContent = try {
            storedSessions?.sortedByDescending { it.lastUsedAt }
                ?.mapNotNull { storedSession ->
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
                        }?.also { store.olm.storeOlmSession(olmSession, senderIdentityKey, olmPickleKey) }
                    }
                }?.firstOrNull()
                ?: if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                    if (hasCreatedTooManyOlmSessions(storedSessions).not()) {
                        log.debug { "decrypt olm event with new session for device with key $senderIdentityKey" }
                        freeAfter(
                            OlmSession.createInboundFrom(account, senderIdentityKey.value, ciphertext.body)
                        ) { olmSession ->
                            val decrypted = olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                            account.removeOneTimeKeys(olmSession)
                            store.olm.storeAccount(account, olmPickleKey)
                            store.olm.storeOlmSession(olmSession, senderIdentityKey, olmPickleKey)
                            decrypted
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
                            senderId to mapOf(senderDeviceId to encryptOlm(DummyEventContent, senderId, senderDeviceId))
                        )
                    )
                } catch (sendError: Throwable) {
                    log.warn { "could not send m.dummy to $senderId ($senderDeviceId)" }
                }
            }
            throw decryptError
        }

        val serializer = json.serializersModule.getContextual(OlmEvent::class)
        requireNotNull(serializer)
        val decryptedEvent = json.decodeFromString(serializer, decryptedContent)

        if (decryptedEvent.sender != senderId
            || decryptedEvent.recipient != ownUserId
            || decryptedEvent.recipientKeys.get<Ed25519Key>()?.value != ownEd25519Key.value
            || decryptedEvent.senderKeys.get<Ed25519Key>()?.value?.let {
                store.keys.getDeviceKeyByValue<Ed25519Key>(senderId, it)
            } == null
        ) throw SessionException.ValidationFailed

        return decryptedEvent.also { log.debug { "decrypted event: $it" } }
    }

    suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): MegolmEncryptedEventContent {
        val storedSession = store.olm.getOutboundMegolmSession(roomId)
        val rotationPeriodMs = settings.rotationPeriodMs
        val rotationPeriodMsgs = settings.rotationPeriodMsgs
        return if (
            storedSession == null
            || rotationPeriodMs != null && (storedSession
                .createdAt.plus(rotationPeriodMs, MILLISECOND) <= Clock.System.now())
            || rotationPeriodMsgs != null && (storedSession.encryptedMessageCount >= rotationPeriodMsgs)
        ) {
            log.debug { "encrypt megolm event with new session" }
            val members = store.roomState.members(roomId, JOIN, INVITE)
            store.keys.waitForUpdateOutdatedKey(*members.toTypedArray())
            val newUserDevices =
                members.mapNotNull { userId ->
                    store.keys.getDeviceKeys(userId)?.let { userId to it.keys }
                }.toMap()
            freeAfter(OlmOutboundGroupSession.create()) { session ->
                store.olm.storeInboundMegolmSession(
                    roomId = roomId,
                    senderKey = ownCurve25519Key,
                    sessionId = session.sessionId,
                    sessionKey = session.sessionKey,
                    pickleKey = olmPickleKey
                )
                encryptWithMegolmSession(session, content, roomId, newUserDevices)
            }
        } else {
            log.debug { "encrypt megolm event with existing session" }
            freeAfter(OlmOutboundGroupSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                encryptWithMegolmSession(session, content, roomId, storedSession.newDevices)
            }
        }
    }

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
            api.users.sendToDevice(
                newUserDevicesWithoutUs.mapValues { (user, devices) ->
                    devices.filterNot { user == ownUserId && it == ownDeviceId }
                        .mapNotNull { deviceName ->
                            try {
                                deviceName to encryptOlm(roomKeyEventContent, user, deviceName)
                            } catch (e: Throwable) {
                                log.debug { "could not encrypt olm: ${e.stackTraceToString()}" }
                                null
                            }
                        }.toMap()
                }
            )
        }

        val serializer = json.serializersModule.getContextual(MegolmEvent::class)
        val event = MegolmEvent(content, roomId).also { log.debug { "megolm event: $it" } }
        requireNotNull(serializer)

        val encryptedContent = session.encrypt(json.encodeToString(serializer, event))
        store.olm.updateOutboundMegolmSession(roomId) { oldStoredSession ->
            oldStoredSession?.copy(
                encryptedMessageCount = oldStoredSession.encryptedMessageCount + 1,
                pickled = session.pickle(olmPickleKey),
                newDevices = emptyMap()
            ) ?: StoredOutboundMegolmSession(
                roomId = roomId,
                pickled = session.pickle(olmPickleKey),
            )
        }

        return MegolmEncryptedEventContent(
            ciphertext = encryptedContent,
            senderKey = ownCurve25519Key,
            deviceId = ownDeviceId,
            sessionId = session.sessionId,
        ).also { log.debug { "encrypted event: $it" } }
    }

    suspend fun decryptMegolm(encryptedEvent: MessageEvent<MegolmEncryptedEventContent>): MegolmEvent<*> {
        val roomId = encryptedEvent.roomId
        val encryptedContent = encryptedEvent.content
        val sessionId = encryptedContent.sessionId
        val senderKey = encryptedContent.senderKey

        val storedSession = store.olm.getInboundMegolmSession(senderKey, sessionId, roomId)
            ?: throw DecryptionException.SenderDidNotSendMegolmKeysToUs

        val decryptionResult = try {
            freeAfter(OlmInboundGroupSession.unpickle(olmPickleKey, storedSession.pickled)) { session ->
                session.decrypt(encryptedContent.ciphertext)
            }
        } catch (e: OlmLibraryException) {
            throw DecryptionException.SessionException(e)
        }

        val serializer = json.serializersModule.getContextual(MegolmEvent::class)
        requireNotNull(serializer)

        val decryptedEvent = json.decodeFromString(serializer, decryptionResult.message)
        val index = decryptionResult.index
        store.olm.updateInboundMegolmMessageIndex(senderKey, sessionId, roomId, index) { storedIndex ->
            if (encryptedEvent.roomId != decryptedEvent.roomId
                || storedIndex?.let { it.eventId != encryptedEvent.id || it.originTimestamp != encryptedEvent.originTimestamp } == true
            ) throw DecryptionException.ValidationFailed

            storedIndex ?: StoredInboundMegolmMessageIndex(
                senderKey, sessionId, roomId, index, encryptedEvent.id, encryptedEvent.originTimestamp
            )
        }

        return decryptedEvent.also { log.debug { "decrypted event: $it" } }
    }

    private fun hasCreatedTooManyOlmSessions(storedSessions: Set<StoredOlmSession>?): Boolean {
        val now = Clock.System.now()
        return (storedSessions?.size ?: 0) >= 5 && storedSessions
            ?.sortedByDescending { it.createdAt }
            ?.takeLast(5)
            ?.map { it.createdAt.plus(1, DateTimeUnit.HOUR) <= now }
            ?.all { true } == true
    }
}