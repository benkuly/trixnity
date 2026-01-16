package de.connect2x.trixnity.client.room

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.OutgoingRoomKeyRequestEventHandlerMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAuthData
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService.DecryptMegolmError
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

class MegolmRoomEventDecryptionServiceTest : TrixnityBaseTest() {
    private val alice = UserId("alice", "server")
    private val room = simpleRoom.roomId

    private val roomStore = getInMemoryRoomStore {
        update(room) { simpleRoom.copy(encrypted = true) }
    }

    private val roomStateStore = getInMemoryRoomStateStore {
        save(
            ClientEvent.RoomEvent.StateEvent(
                EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm),
                EventId("enc_state"),
                alice,
                room,
                1234,
                stateKey = "",
            )
        )
    }

    private val olmCryptoStore = getInMemoryOlmStore()

    private val keyBackupServiceMock = KeyBackupServiceMock().apply {
        version.value = GetRoomKeysBackupVersionResponse.V1(
            RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Curve25519KeyValue("")),
            1, "", ""
        )
    }
    private val olmEncyptionServiceMock = OlmEncryptionServiceMock()
    private val outgoingRoomKeyRequestEventHandlerMock = OutgoingRoomKeyRequestEventHandlerMock()

    private val cut = MegolmRoomEventEncryptionService(
        roomStore = roomStore,
        loadMembersService = { _, _ -> },
        roomStateStore = roomStateStore,
        olmCryptoStore = olmCryptoStore,
        keyBackupService = keyBackupServiceMock,
        outgoingRoomKeyRequestEventHandler = outgoingRoomKeyRequestEventHandlerMock,
        olmEncryptionService = olmEncyptionServiceMock
    )

    private val session = "SESSION"
    private val senderKey = Key.Curve25519Key(null, "senderKey")
    private val storedSession = StoredInboundMegolmSession(
        senderKey.value, Ed25519KeyValue("ed"), session, room, 1, hasBeenBackedUp = false, isTrusted = false,
        forwardingCurve25519KeyChain = listOf(), pickled = "pickle"
    )
    private val encryptedEvent = MessageEvent(
        MegolmEncryptedMessageEventContent(MegolmMessageValue("cipher cipher"), sessionId = session),
        EventId("$1event"),
        alice,
        room,
        1234
    )
    private val expectedDecryptedEvent =
        DecryptedMegolmEvent(RoomMessageEventContent.TextBased.Text("decrypted"), room)

    @Test
    fun `encrypt » return null when room does not exist`() = runTest {
        roomStateStore.save(
            ClientEvent.RoomEvent.StateEvent(
                EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                EventId("enc_state"),
                alice,
                room,
                1234,
                stateKey = "",
            )
        )
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), RoomId("unknown")) shouldBe null
    }

    @Test
    fun `encrypt » only encrypt in room with megolm algorithm`() = runTest {
        roomStateStore.save(
            ClientEvent.RoomEvent.StateEvent(
                EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                EventId("enc_state"),
                alice,
                room,
                1234,
                stateKey = "",
            )
        )
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room) shouldBe null
    }

    @Test
    fun `encrypt » encrypt`() = runTest {
        val encryptedEvent = MegolmEncryptedMessageEventContent(MegolmMessageValue("cipher"), sessionId = "sessionId")
        olmEncyptionServiceMock.returnEncryptMegolm = Result.success(encryptedEvent)
        cut.encrypt(RoomMessageEventContent.TextBased.Text("hi"), room) shouldBe Result.success(
            encryptedEvent
        )
    }

    @Test
    fun `decrypt » return null when unsupported`() = runTest {
        cut.decrypt(
            MessageEvent(
                RoomMessageEventContent.TextBased.Text("unsupported"),
                EventId("$1event"),
                alice,
                room,
                1234
            )
        ) shouldBe null
    }

    @Test
    fun `decrypt » only decrypt in room with megolm alroithm`() = runTest {
        roomStateStore.save(
            ClientEvent.RoomEvent.StateEvent(
                EncryptionEventContent(algorithm = EncryptionAlgorithm.Unknown("super-duper-crypto")),
                EventId("enc_state"),
                alice,
                room,
                1234,
                stateKey = "",
            )
        )
        olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        cut.decrypt(encryptedEvent) shouldBe null
    }

    @Test
    fun `decrypt » decrypt event`() = runTest {
        olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        cut.decrypt(encryptedEvent).shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
    }

    @Test
    fun `decrypt » handle error`() = runTest {
        olmEncyptionServiceMock.returnDecryptMegolm.add(
            Result.failure(DecryptMegolmError.ValidationFailed(""))
        )
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        cut.decrypt(encryptedEvent).shouldNotBeNull()
            .exceptionOrNull() shouldBe DecryptMegolmError.ValidationFailed("")
    }

    @Test
    fun `decrypt » wait for olm session and ask key backup for it`() = runTest {
        olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        val result = async { cut.decrypt(encryptedEvent) }
        delay(20)
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        delay(20)
        result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
    }

    @Test
    fun `decrypt » wait for olm session and ask other device for it when key backup disabled`() = runTest {
        keyBackupServiceMock.version.value = null
        olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

        val result = async { cut.decrypt(encryptedEvent) }
        delay(20)
        olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
        delay(20)
        result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.first() shouldBe Pair(room, session)
    }

    @Test
    fun `decrypt » wait for olm session and ask key backup for it when existing session does not known the index`() =
        runTest {
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.MegolmKeyUnknownMessageIndex()))
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

            olmCryptoStore.updateInboundMegolmSession(session, room) {
                storedSession.copy(firstKnownIndex = 4)
            }
            val result = async { cut.decrypt(encryptedEvent) }

            delay(50)
            olmCryptoStore.updateInboundMegolmSession(session, room) {
                storedSession.copy(firstKnownIndex = 3)
            }
            result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            keyBackupServiceMock.loadMegolmSessionCalled.value.size shouldBe 1
            keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
        }
}