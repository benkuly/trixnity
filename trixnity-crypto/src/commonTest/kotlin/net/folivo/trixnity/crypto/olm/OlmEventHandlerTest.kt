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
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.ClientEventEmitterImpl
import net.folivo.trixnity.core.Unsubscriber
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
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
            eventEmitter,
            olmKeysChangeEmitter,
            OlmDecrypterMock(),
            SignServiceMock().apply { signCurve25519Key = Key.SignedCurve25519Key(null, "", mapOf()) },
            olmEventHandlerRequestHandlerMock,
            olmStoreMock,
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

        val job = launch {
            cut.forgetOldFallbackKey()
        }

        olmStoreMock.forgetFallbackKeyAfter.value = Clock.System.now()

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
        job.cancel()
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
        captureFallbackKeys[0].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>().fallback shouldBe true
        captureFallbackKeys[1].keys shouldHaveSize 1
        captureFallbackKeys[1].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>().fallback shouldBe true

        captureFallbackKeys[0].keys shouldNotContainAnyOf captureFallbackKeys[1].keys
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
    should("remove megolm session on leave or ban") {
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

        olmStoreMock.outboundMegolmSession[roomId] = StoredOutboundMegolmSession(roomId, pickled = "")
        cut.handleMemberEvents(
            listOf(
                StateEvent(
                    MemberEventContent(membership = Membership.BAN),
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
})