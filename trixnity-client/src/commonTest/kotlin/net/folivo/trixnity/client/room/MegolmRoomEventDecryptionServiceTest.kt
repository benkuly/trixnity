package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.mocks.OutgoingRoomKeyRequestEventHandlerMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.DecryptedMegolmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.OlmLibraryException

class MegolmRoomEventDecryptionServiceTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var keyBackupServiceMock: KeyBackupServiceMock
    lateinit var olmEncyptionServiceMock: OlmEncryptionServiceMock
    lateinit var outgoingRoomKeyRequestEventHandlerMock: OutgoingRoomKeyRequestEventHandlerMock
    lateinit var scope: CoroutineScope

    lateinit var cut: MegolmRoomEventDecryptionService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        olmCryptoStore = getInMemoryOlmStore(scope)
        keyBackupServiceMock = KeyBackupServiceMock()
        keyBackupServiceMock.version.value = GetRoomKeysBackupVersionResponse.V1(
            RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Key.Curve25519Key(null, "")),
            1, "", ""
        )
        olmEncyptionServiceMock = OlmEncryptionServiceMock()
        outgoingRoomKeyRequestEventHandlerMock = OutgoingRoomKeyRequestEventHandlerMock()
        cut = MegolmRoomEventDecryptionService(
            olmCryptoStore, keyBackupServiceMock, outgoingRoomKeyRequestEventHandlerMock, olmEncyptionServiceMock
        )
    }

    afterTest {
        scope.cancel()
    }

    context(MegolmRoomEventDecryptionService::decrypt.name) {
        val session = "SESSION"
        val senderKey = Key.Curve25519Key(null, "senderKey")
        val storedSession = StoredInboundMegolmSession(
            senderKey, Key.Ed25519Key(null, "ed"), session, room, 1, hasBeenBackedUp = false, isTrusted = false,
            forwardingCurve25519KeyChain = listOf(), pickled = "pickle"
        )
        val encryptedEvent = MessageEvent(
            EncryptedEventContent.MegolmEncryptedEventContent("cipher cipher", sessionId = session),
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
        should("decrypt event") {
            olmEncyptionServiceMock.returnDecryptMegolm.add { expectedDecryptedEvent }
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            cut.decrypt(encryptedEvent).shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
        }
        should("handle error") {
            olmEncyptionServiceMock.returnDecryptMegolm.add { throw DecryptionException.ValidationFailed("") }
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            cut.decrypt(encryptedEvent).shouldNotBeNull().exceptionOrNull() shouldBe
                    DecryptionException.ValidationFailed("")
        }
        should("wait for olm session and ask key backup for it") {
            olmEncyptionServiceMock.returnDecryptMegolm.add { expectedDecryptedEvent }

            val result = async { cut.decrypt(encryptedEvent) }
            delay(20)
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            delay(20)
            result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            keyBackupServiceMock.loadMegolmSessionCalled.value.first() shouldBe Pair(room, session)
        }
        should("wait for olm session and ask other device for it, when key backup disabled") {
            keyBackupServiceMock.version.value = null
            olmEncyptionServiceMock.returnDecryptMegolm.add { expectedDecryptedEvent }

            val result = async { cut.decrypt(encryptedEvent) }
            delay(20)
            olmCryptoStore.updateInboundMegolmSession(session, room) { storedSession }
            delay(20)
            result.await().shouldNotBeNull().getOrThrow() shouldBe expectedDecryptedEvent.content
            outgoingRoomKeyRequestEventHandlerMock.requestRoomKeysCalled.value.first() shouldBe Pair(room, session)
        }
        should("wait for olm session and ask key backup for it when existing session does not known the index") {
            olmEncyptionServiceMock.returnDecryptMegolm.add { throw OlmLibraryException("OLM_UNKNOWN_MESSAGE_INDEX") }
            olmEncyptionServiceMock.returnDecryptMegolm.add { expectedDecryptedEvent }

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