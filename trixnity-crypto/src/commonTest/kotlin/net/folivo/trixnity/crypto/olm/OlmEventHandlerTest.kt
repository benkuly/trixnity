package net.folivo.trixnity.crypto.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.mocks.OlmDecrypterMock
import net.folivo.trixnity.crypto.mocks.OlmEventHandlerRequestHandlerMock
import net.folivo.trixnity.crypto.mocks.OlmStoreMock
import net.folivo.trixnity.crypto.mocks.SignServiceMock
import net.folivo.trixnity.olm.*
import kotlin.time.Duration.Companion.seconds

class OlmEventHandlerTest : ShouldSpec({
    timeout = 30_000

    lateinit var cut: OlmEventHandler

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val roomId = RoomId("room", "server")

    lateinit var olmStoreMock: OlmStoreMock
    lateinit var olmEventHandlerRequestHandlerMock: OlmEventHandlerRequestHandlerMock

    beforeEach {
        olmStoreMock = OlmStoreMock()
        olmEventHandlerRequestHandlerMock = OlmEventHandlerRequestHandlerMock()

        olmStoreMock.olmAccount.value = freeAfter(OlmAccount.create()) { it.pickle("") }

        val eventEmitter: ClientEventEmitterImpl<List<ClientEvent<*>>> =
            object : ClientEventEmitterImpl<List<ClientEvent<*>>>() {}
        val olmKeysChangeEmitter: OlmKeysChangeEmitter = object : OlmKeysChangeEmitter {
            override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit): Unsubscriber {
                throw NotImplementedError()
            }
        }

        cut = OlmEventHandler(
            UserInfo(UserId(""), "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            eventEmitter,
            olmKeysChangeEmitter,
            OlmDecrypterMock(),
            SignServiceMock().apply { signCurve25519Key = Key.SignedCurve25519Key(null, "", mapOf()) },
            olmEventHandlerRequestHandlerMock,
            olmStoreMock,
            Clock.System,
        )
    }

    // ##########################
    // forgetOldFallbackKey
    // ##########################
    should("forget old fallback key after timestamp") {
        data class OlmInfos(
            val olmAccount: String,
            val fallbackKey: String
        )

        val olmInfos =
            freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
                val bobIdentityKey = bobAccount.identityKeys.curve25519
                bobAccount.generateFallbackKey()
                val bobFallbackKey = bobAccount.unpublishedFallbackKey.curve25519.values.first()
                bobAccount.markKeysAsPublished()
                bobAccount.generateFallbackKey() // we need 2 fallback keys to forget one
                bobAccount.markKeysAsPublished()

                val message =
                    freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobFallbackKey)) { aliceSession ->
                        aliceSession.encrypt("Hello bob , this is alice!")
                    }

                val decryptedMessage =
                    freeAfter(OlmSession.createInbound(bobAccount, message.cipherText)) { bobSession ->
                        bobSession.decrypt(message)
                    }

                decryptedMessage shouldBe "Hello bob , this is alice!"
                OlmInfos(
                    olmAccount = bobAccount.pickle(""),
                    fallbackKey = bobFallbackKey,
                )
            }
        olmStoreMock.olmAccount.value = olmInfos.olmAccount

        olmStoreMock.forgetFallbackKeyAfter.value = Clock.System.now() - 1.seconds

        cut.forgetOldFallbackKey()

        olmStoreMock.forgetFallbackKeyAfter.first { it == null }

        olmStoreMock.olmAccount.first { it != olmInfos.olmAccount }

        freeAfter(
            OlmAccount.unpickle("", checkNotNull(olmStoreMock.olmAccount.value)),
            OlmAccount.create()
        ) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519

            val message =
                freeAfter(
                    OlmSession.createOutbound(aliceAccount, bobIdentityKey, olmInfos.fallbackKey)
                ) { aliceSession ->
                    aliceSession.encrypt("Hello bob , this is alice!")
                }

            shouldThrow<OlmLibraryException> {
                freeAfter(OlmSession.createInbound(bobAccount, message.cipherText)) { bobSession ->
                    bobSession.decrypt(message)
                }
            }.message shouldBe "BAD_MESSAGE_KEY_ID"
        }
    }

    // ##########################
    // handleOlmKeysChange
    // ##########################
    should("create and upload new one time keys when server has 49 one time keys") {
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 49), null))
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 0), null))

        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys shouldHaveSize 2
        captureOneTimeKeys[0].keys shouldHaveSize 26
        captureOneTimeKeys[1].keys shouldHaveSize 75

        captureOneTimeKeys[0].keys shouldNotContainAnyOf captureOneTimeKeys[1].keys
    }
    should("re-upload generated keys when failed") {
        olmEventHandlerRequestHandlerMock.setOneTimeKeys =
            Result.failure(
                MatrixServerException(
                    HttpStatusCode.BadGateway,
                    ErrorResponse.Unknown("bad gateway")
                )
            )
        shouldThrow<MatrixServerException> {
            cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 49), setOf()))
        }
        olmEventHandlerRequestHandlerMock.setOneTimeKeys = Result.success(Unit)
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 49), setOf()))

        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys shouldHaveSize 2
        captureOneTimeKeys[0].keys shouldHaveSize 26
        captureOneTimeKeys[0].keys shouldBe captureOneTimeKeys[1].keys
    }
    should("not fail when re-upload gives 4xx failure because we most likely already uploaded them") {
        olmEventHandlerRequestHandlerMock.setOneTimeKeys =
            Result.failure(
                MatrixServerException(
                    HttpStatusCode.BadGateway,
                    ErrorResponse.Unknown("bad gateway")
                )
            )
        shouldThrow<MatrixServerException> {
            cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 49), setOf()))
        }
        olmEventHandlerRequestHandlerMock.setOneTimeKeys =
            Result.failure(
                MatrixServerException(
                    HttpStatusCode.BadRequest,
                    ErrorResponse.Unknown("key already exist")
                )
            )
        cut.handleOlmKeysChange( // should re-upload even if the server says it does not need to
            OlmKeysChange(
                mapOf(KeyAlgorithm.SignedCurve25519 to 0),
                setOf(KeyAlgorithm.SignedCurve25519)
            )
        )

        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys shouldHaveSize 2
        captureOneTimeKeys[0].keys shouldHaveSize 26
        captureOneTimeKeys[0].keys shouldBe captureOneTimeKeys[1].keys
    }
    should("not upload keys when server has 50 one time keys") {
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 50), null))
        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys should beEmpty()
    }

    should("create and upload fallback key, when missing") {
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf()))
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf()))

        val captureFallbackKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.second }
        captureFallbackKeys shouldHaveSize 2
        captureFallbackKeys[0].keys shouldHaveSize 1
        val fallbackKey1 = captureFallbackKeys[0].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>()
        fallbackKey1.fallback shouldBe true
        captureFallbackKeys[1].keys shouldHaveSize 1
        val fallbackKey2 = captureFallbackKeys[1].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>()
        fallbackKey2.fallback shouldBe true

        fallbackKey1 shouldNotBe fallbackKey2
    }
    should("not upload fallback key when server has one") {
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf(KeyAlgorithm.SignedCurve25519)))
        val captureFallbackKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.second }
        captureFallbackKeys should beEmpty()
    }

    // ##########################
    // handleOlmEncryptedRoomKeyEventContent
    // ##########################
    should("store inbound megolm session") {
        val outboundSession = OlmOutboundGroupSession.create()

        val eventContent = RoomKeyEventContent(
            roomId,
            outboundSession.sessionId,
            outboundSession.sessionKey,
            EncryptionAlgorithm.Megolm
        )
        val encryptedEvent = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Key.Curve25519Key(null, "BOB_IDEN"),
            ), bob
        )

        cut.handleOlmEncryptedRoomKeyEventContent(
            DecryptedOlmEventContainer(
                encryptedEvent,
                DecryptedOlmEvent(
                    eventContent,
                    bob,
                    keysOf(Key.Ed25519Key(null, "BOB_SIGN")),
                    alice,
                    keysOf()
                )
            )
        )

        assertSoftly(
            olmStoreMock.inboundMegolmSession[outboundSession.sessionId to roomId]
                .shouldNotBeNull()
        ) {
            roomId shouldBe roomId
            sessionId shouldBe outboundSession.sessionId
            senderKey shouldBe Key.Curve25519Key(null, "BOB_IDEN")
            senderSigningKey shouldBe Key.Ed25519Key(null, "BOB_SIGN")
        }
    }

    // ##########################
    // handleMemberEvents
    // ##########################
    should("remove megolm session") {
        olmStoreMock.roomEncryptionAlgorithm[roomId] = EncryptionAlgorithm.Megolm

        olmStoreMock.outboundMegolmSession[roomId] = StoredOutboundMegolmSession(roomId, pickled = "")
        cut.handleMemberEvents(
            listOf(
                StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("\$event"),
                    alice,
                    roomId,
                    1234,
                    stateKey = alice.full
                )
            )
        )
        olmStoreMock.outboundMegolmSession[roomId] shouldBe null
    }
    should("update new devices in megolm session") {
        olmStoreMock.roomEncryptionAlgorithm[roomId] = EncryptionAlgorithm.Megolm
        olmStoreMock.historyVisibility = HistoryVisibilityEventContent.HistoryVisibility.SHARED
        olmStoreMock.devices[roomId] = mapOf(alice to setOf("A1", "A2"))

        val megolmSession = StoredOutboundMegolmSession(roomId, pickled = "")
        olmStoreMock.outboundMegolmSession[roomId] = megolmSession
        cut.handleMemberEvents(
            listOf(
                StateEvent(
                    MemberEventContent(membership = Membership.KNOCK),
                    EventId("\$event"),
                    alice,
                    roomId,
                    1234,
                    stateKey = alice.full
                )
            )
        )
        olmStoreMock.outboundMegolmSession[roomId] shouldBe megolmSession.copy(
            newDevices = mapOf(alice to setOf("A1", "A2"))
        )
    }
})