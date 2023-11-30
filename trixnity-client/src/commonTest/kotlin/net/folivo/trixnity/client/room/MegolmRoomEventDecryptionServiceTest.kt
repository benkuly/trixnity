package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.mocks.OutgoingRoomKeyRequestEventHandlerMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.DecryptedMegolmEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.DecryptMegolmError
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

class MegolmRoomEventDecryptionServiceTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var userService: UserServiceMock
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var keyBackupServiceMock: KeyBackupServiceMock
    lateinit var olmEncyptionServiceMock: OlmEncryptionServiceMock
    lateinit var outgoingRoomKeyRequestEventHandlerMock: OutgoingRoomKeyRequestEventHandlerMock
    lateinit var scope: CoroutineScope

    lateinit var cut: MegolmRoomEventEncryptionService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        userService = UserServiceMock()
        roomStore = getInMemoryRoomStore(scope)
        roomStore.update(room) { simpleRoom.copy(encrypted = true) }
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomStateStore.save(
            ClientEvent.RoomEvent.StateEvent(
                EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm),
                EventId("enc_state"),
                alice,
                room,
                1234,
                stateKey = "",
            )
        )
        olmCryptoStore = getInMemoryOlmStore(scope)
        keyBackupServiceMock = KeyBackupServiceMock()
        keyBackupServiceMock.version.value = GetRoomKeysBackupVersionResponse.V1(
            RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Key.Curve25519Key(null, "")),
            1, "", ""
        )
        olmEncyptionServiceMock = OlmEncryptionServiceMock()
        outgoingRoomKeyRequestEventHandlerMock = OutgoingRoomKeyRequestEventHandlerMock()
        cut = MegolmRoomEventEncryptionService(
            roomStore = roomStore,
            userService = userService,
            roomStateStore = roomStateStore,
            olmCryptoStore = olmCryptoStore,
            keyBackupService = keyBackupServiceMock,
            outgoingRoomKeyRequestEventHandler = outgoingRoomKeyRequestEventHandlerMock,
            olmEncryptionService = olmEncyptionServiceMock
        )
    }

    afterTest {
        scope.cancel()
    }

    context(MegolmRoomEventEncryptionService::encrypt.name) {
        should("only encrypt in room with megolm algorithm") {
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
            cut.encrypt(RoomMessageEventContent.TextMessageEventContent("hi"), room) shouldBe null
        }
        should("encrypt") {
            val encryptedEvent = MegolmEncryptedMessageEventContent("cipher", sessionId = "sessionId")
            olmEncyptionServiceMock.returnEncryptMegolm = Result.success(encryptedEvent)
            cut.encrypt(RoomMessageEventContent.TextMessageEventContent("hi"), room) shouldBe Result.success(
                encryptedEvent
            )
        }
    }

    context(MegolmRoomEventEncryptionService::decrypt.name) {
        val session = "SESSION"
        val senderKey = Key.Curve25519Key(null, "senderKey")
        val storedSession = StoredInboundMegolmSession(
            senderKey, Key.Ed25519Key(null, "ed"), session, room, 1, hasBeenBackedUp = false, isTrusted = false,
            forwardingCurve25519KeyChain = listOf(), pickled = "pickle"
        )
        val encryptedEvent = MessageEvent(
            MegolmEncryptedMessageEventContent("cipher cipher", sessionId = session),
            EventId("$1event"),
            alice,
            room,
            1234
        )
        val expectedDecryptedEvent =
            DecryptedMegolmEvent(RoomMessageEventContent.TextMessageEventContent("decrypted"), room)
        should("return null when unsupported") {
            cut.decrypt(
                MessageEvent(
                    RoomMessageEventContent.TextMessageEventContent("unsupported"),
                    EventId("$1event"),
                    alice,
                    room,
                    1234
                )
            ) shouldBe null
        }
        should("only decrypt in room with megolm alroithm") {
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
        should("decrypt event") {
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            cut.decrypt(encryptedEvent).shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        }
        should("handle error") {
            olmEncyptionServiceMock.returnDecryptMegolm.add(
                Result.failure(DecryptMegolmError.ValidationFailed(""))
            )
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            cut.decrypt(encryptedEvent).shouldNotBeNull()
                .exceptionOrNull() shouldBe DecryptMegolmError.ValidationFailed("")
        }
        should("wait for olm session and ask key backup for it") {
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

            val result = async { cut.decrypt(encryptedEvent) }
            delay(20)
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            delay(20)
            result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
        }
        should("wait for olm session and ask other device for it, when key backup disabled") {
            keyBackupServiceMock.version.value = null
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.success(expectedDecryptedEvent))

            val result = async { cut.decrypt(encryptedEvent) }
            delay(20)
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            delay(20)
            result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.first() shouldBe Pair(room, session)
        }
        should("wait for olm session and ask key backup for it when existing session does not known the index") {
            olmEncyptionServiceMock.returnDecryptMegolm.add(Result.failure(DecryptMegolmError.MegolmKeyUnknownMessageIndex))
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
})