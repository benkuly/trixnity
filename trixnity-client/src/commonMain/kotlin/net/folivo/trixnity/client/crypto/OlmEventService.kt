package net.folivo.trixnity.client.crypto

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DateTimeUnit.Companion.MILLISECOND
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
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
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalSerializationApi::class)
class OlmEventService internal constructor(
    private val json: Json,
    private val account: OlmAccount,
    private val store: Store,
    private val secureStore: SecureStore,
    private val api: MatrixApiClient,
    private val signService: OlmSignService,
    loggerFactory: LoggerFactory
) {

    private val log = newLogger(loggerFactory)

    private val myUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")
    private val myDeviceId = store.account.deviceId.value ?: throw IllegalArgumentException("deviceId must not be null")
    private val myEd25519Key = Ed25519Key(myDeviceId, account.identityKeys.ed25519)
    private val myCurve25519Key = Curve25519Key(myDeviceId, account.identityKeys.curve25519)

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
            val response = api.keys.claimKeys(mapOf(receiverId to mapOf(deviceId to KeyAlgorithm.SignedCurve25519)))
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
                    store.olm.storeOlmSession(session, identityKey, secureStore.olmPickleKey)
                }
            }
        } else {
            log.debug { "encrypt olm event with existing session for device with key $identityKey" }
            freeAfter(OlmSession.unpickle(secureStore.olmPickleKey, storedSession.pickle)) { session ->
                encryptWithOlmSession(session, content, receiverId, deviceId, identityKey).also {
                    store.olm.storeOlmSession(session, identityKey, secureStore.olmPickleKey)
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
            sender = myUserId,
            senderKeys = keysOf(myEd25519Key.copy(keyId = null)),
            recipient = receiverId,
            recipientKeys = keysOf(
                store.keys.getOrFetchKeyFromDevice<Ed25519Key>(receiverId, deviceId)?.copy(keyId = null)
                    ?: throw KeyNotFoundException("could not find es25519 key for $receiverId ($deviceId)")
            )
        ).also { log.debug { "olm event: $it" } }
        requireNotNull(serializer)
        val encryptedContent = olmSession.encrypt(json.encodeToString(serializer, event))
        store.olm.storeOlmSession(olmSession, identityKey, secureStore.olmPickleKey)
        return OlmEncryptedEventContent(
            ciphertext = mapOf(
                identityKey.value to CiphertextInfo(
                    encryptedContent.cipherText,
                    OlmMessageType.of(encryptedContent.type.value)
                )
            ),
            senderKey = myCurve25519Key
        ).also { log.debug { "encrypted event: $it" } }
    }

    suspend fun decryptOlm(encryptedContent: OlmEncryptedEventContent, senderId: UserId): OlmEvent<*> {
        log.debug { "start decrypt olm event $encryptedContent" }
        val ciphertext = encryptedContent.ciphertext[myCurve25519Key.value]
            ?: throw SessionException.SenderDidNotEncryptForThisDeviceException
        val senderIdentityKey =
            store.keys.getDeviceKeyByValue<Curve25519Key>(senderId, encryptedContent.senderKey.value)
                ?: throw KeyVerificationFailedException("the sender key of the event is not known for this device")
        val storedSessions = store.olm.getOlmSessions(senderIdentityKey)
        val decryptedContent = try {
            storedSessions?.sortedByDescending { it.lastUsedAt }
                ?.mapNotNull { storedSession ->
                    freeAfter(OlmSession.unpickle(secureStore.olmPickleKey, storedSession.pickle)) { olmSession ->
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
                                log.warning { "could not decrypt olm event with existing session ${storedSession.sessionId} for device with key $senderIdentityKey. Reason: ${error.message}" }
                                null
                            }
                        }?.also { store.olm.storeOlmSession(olmSession, senderIdentityKey, secureStore.olmPickleKey) }
                    }
                }?.firstOrNull()
                ?: if (ciphertext.type == OlmMessageType.INITIAL_PRE_KEY) {
                    val now = Clock.System.now()
                    val tooManyNewSessions = (storedSessions?.size ?: 0) >= 5 && storedSessions
                        ?.sortedByDescending { it.createdAt }
                        ?.takeLast(5)
                        ?.map { it.createdAt.plus(1, DateTimeUnit.HOUR) <= now }
                        ?.all { true } == true
                    if (!tooManyNewSessions) {
                        log.debug { "decrypt olm event with new session for device with key $senderIdentityKey" }
                        freeAfter(
                            OlmSession.createInboundFrom(account, senderIdentityKey.value, ciphertext.body)
                        ) { olmSession ->
                            val decrypted = olmSession.decrypt(OlmMessage(ciphertext.body, INITIAL_PRE_KEY))
                            account.removeOneTimeKeys(olmSession)
                            store.olm.storeAccount(account, secureStore.olmPickleKey)
                            store.olm.storeOlmSession(olmSession, senderIdentityKey, secureStore.olmPickleKey)
                            decrypted
                        }
                    } else throw SessionException.PreventToManySessions
                } else {
                    throw SessionException.CouldNotDecrypt
                }
        } catch (decryptError: Throwable) {
            val senderDeviceId =
                store.keys.getDeviceKeys(senderId)?.entries
                    ?.find { it.value.value.signed.keys.contains(senderIdentityKey) }?.key
                    ?: throw KeyVerificationFailedException("the sender key of the event is not known for this device")
            try {
                log.debug { "try recover corrupted olm session by sending a dummy event" }
                api.users.sendToDevice(
                    mapOf(senderId to mapOf(senderDeviceId to encryptOlm(DummyEventContent, senderId, senderDeviceId)))
                )
            } catch (sendError: Throwable) {
                log.warning { "could not send m.dummy to $senderId ($senderDeviceId)" }
            }
            throw decryptError
        }

        val serializer = json.serializersModule.getContextual(OlmEvent::class)
        requireNotNull(serializer)
        val decryptedEvent = json.decodeFromString(serializer, decryptedContent)

        if (decryptedEvent.sender != senderId
            || decryptedEvent.recipient != myUserId
            || decryptedEvent.recipientKeys.get<Ed25519Key>()?.value != myEd25519Key.value
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
                    senderKey = myCurve25519Key,
                    sessionId = session.sessionId,
                    sessionKey = session.sessionKey,
                    pickleKey = secureStore.olmPickleKey
                )
                encryptWithMegolmSession(session, content, roomId, newUserDevices)
            }
        } else {
            log.debug { "encrypt megolm event with existing session" }
            freeAfter(OlmOutboundGroupSession.unpickle(secureStore.olmPickleKey, storedSession.pickle)) { session ->
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
            .mapValues { (userId, deviceIds) -> if (userId == myUserId) deviceIds - myDeviceId else deviceIds }
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
                    devices.filterNot { user == myUserId && it == myDeviceId }
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
                pickle = session.pickle(secureStore.olmPickleKey),
                newDevices = emptyMap()
            ) ?: StoredOutboundMegolmSession(
                roomId = roomId,
                pickle = session.pickle(secureStore.olmPickleKey),
            )
        }

        return MegolmEncryptedEventContent(
            ciphertext = encryptedContent,
            senderKey = myCurve25519Key,
            deviceId = myDeviceId,
            sessionId = session.sessionId,
        ).also { log.debug { "encrypted event: $it" } }
    }

    suspend fun decryptMegolm(encryptedEvent: MessageEvent<MegolmEncryptedEventContent>): MegolmEvent<*> {
        val roomId = encryptedEvent.roomId
        val encryptedContent = encryptedEvent.content
        val sessionId = encryptedContent.sessionId
        val senderKey = encryptedContent.senderKey

        var decryptionResult: OlmInboundGroupMessage? = null

        store.olm.updateInboundMegolmSession(senderKey, sessionId, roomId) { storedSession ->
            if (storedSession == null) throw DecryptionException.SenderDidNotSendMegolmKeysToUs

            try {
                freeAfter(OlmInboundGroupSession.unpickle(secureStore.olmPickleKey, storedSession.pickle)) { session ->
                    decryptionResult = session.decrypt(encryptedContent.ciphertext)
                    storedSession.copy(pickle = session.pickle(secureStore.olmPickleKey))
                }
            } catch (e: OlmLibraryException) {
                throw DecryptionException.SessionException(e)
            }
        }
        val actualDecryptionResult = decryptionResult
        requireNotNull(actualDecryptionResult)

        val serializer = json.serializersModule.getContextual(MegolmEvent::class)
        requireNotNull(serializer)

        val decryptedEvent = json.decodeFromString(serializer, actualDecryptionResult.message)
        val index = actualDecryptionResult.index
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
}