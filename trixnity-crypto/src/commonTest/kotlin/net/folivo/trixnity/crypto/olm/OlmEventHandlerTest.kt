package net.folivo.trixnity.crypto.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
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
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.crypto.driver.olm.Account
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.crypto.mocks.OlmDecrypterMock
import net.folivo.trixnity.crypto.mocks.OlmEventHandlerRequestHandlerMock
import net.folivo.trixnity.crypto.mocks.OlmStoreMock
import net.folivo.trixnity.crypto.mocks.SignServiceMock
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class OlmEventHandlerTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val account = driver.olm.account
    private val curve25519PublicKey = driver.key.curve25519PublicKey
    private val groupSession = driver.megolm.groupSession

    // You may wonder why these magic numbers? See handleOlmKeysChange.
    private val firstSize = 51
    private val secondSize = 75

    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val roomId = RoomId("!room:server")

    private val dummyPickledAccount = account().use(Account::pickle)

    private val olmStoreMock = OlmStoreMock().apply {
        olmAccount.value = dummyPickledAccount
    }
    private val olmEventHandlerRequestHandlerMock = OlmEventHandlerRequestHandlerMock()

    private val cut: OlmEventHandler

    init {

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
            SignServiceMock().apply { signCurve25519Key = Key.SignedCurve25519Key(null, "", signatures = mapOf()) },
            olmEventHandlerRequestHandlerMock,
            olmStoreMock,
            testScope.testClock,
            driver,
        )
    }

    // ##########################
    // forgetOldFallbackKey
    // ##########################
    @Test
    fun `forget old fallback key after timestamp`() = runTest {
        data class OlmInfos(
            val olmAccount: String,
            val fallbackKey: String
        )

        val bobAccount = account()
        val aliceAccount = account()

        val bobIdentityKey = bobAccount.curve25519Key
        bobAccount.generateFallbackKey()
        val bobFallbackKey = checkNotNull(bobAccount.fallbackKey).second
        assertNotNull(bobFallbackKey)
        bobAccount.markKeysAsPublished()
        bobAccount.generateFallbackKey() // we need 2 fallback keys to forget one
        bobAccount.markKeysAsPublished()

        val aliceSession = aliceAccount.createOutboundSession(
            identityKey = bobIdentityKey, oneTimeKey = bobFallbackKey
        )
        val message = aliceSession.encrypt("Hello bob , this is alice!") as Message.PreKey

        val (decryptedMessage, _) = bobAccount.createInboundSession(
            preKeyMessage = message
        )

        decryptedMessage shouldBe "Hello bob , this is alice!"

        val olmInfos = OlmInfos(
            olmAccount = bobAccount.pickle(),
            fallbackKey = bobFallbackKey.base64,
        )
        olmStoreMock.olmAccount.value = olmInfos.olmAccount

        olmStoreMock.forgetFallbackKeyAfter.value = testClock.now() - 1.seconds

        cut.forgetOldFallbackKey()

        olmStoreMock.forgetFallbackKeyAfter.first { it == null }

        olmStoreMock.olmAccount.first { it != olmInfos.olmAccount }

        val unpickledBobAccount = account.fromPickle(olmStoreMock.olmAccount.value)
        val newAliceAccount = account()

        val newBobIdentityKey = bobAccount.curve25519Key

        val newAliceSession = newAliceAccount.createOutboundSession(
            identityKey = newBobIdentityKey, oneTimeKey = curve25519PublicKey(olmInfos.fallbackKey)
        )

        val newMessage = newAliceSession.encrypt("Hello bob , this is alice!") as Message.PreKey

        shouldThrow<CryptoDriverException> {
            unpickledBobAccount.createInboundSession(
                preKeyMessage = newMessage
            )
        } // TODO: .message shouldBe "The pre-key message contained an unknown one-time key: ${newMessage.sessionKeys.oneTimeKey.base64}"
    }


    // ##########################
    // handleOlmKeysChange
    // ##########################
    @Test
    fun `create and upload new one time keys when server has 49 one time keys`() = runTest {
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 24), null))
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 0), null))

        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys shouldHaveSize 2
        captureOneTimeKeys[0].keys shouldHaveSize firstSize
        captureOneTimeKeys[1].keys shouldHaveSize secondSize

        captureOneTimeKeys[0].keys shouldNotContainAnyOf captureOneTimeKeys[1].keys
    }

    @Test
    fun `re-upload generated keys when failed`() = runTest {
        olmEventHandlerRequestHandlerMock.setOneTimeKeys =
            Result.failure(
                MatrixServerException(
                    HttpStatusCode.BadGateway,
                    ErrorResponse.Unknown("bad gateway")
                )
            )
        shouldThrow<MatrixServerException> {
            cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 24), setOf()))
        }
        olmEventHandlerRequestHandlerMock.setOneTimeKeys = Result.success(Unit)
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 24), setOf()))

        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys shouldHaveSize 2
        captureOneTimeKeys[0].keys shouldHaveSize firstSize
        captureOneTimeKeys[0].keys shouldBe captureOneTimeKeys[1].keys
    }

    @Test
    fun `not fail when re-upload gives 4xx failure because we most likely already uploaded them`() = runTest {
        olmEventHandlerRequestHandlerMock.setOneTimeKeys =
            Result.failure(
                MatrixServerException(
                    HttpStatusCode.BadGateway,
                    ErrorResponse.Unknown("bad gateway")
                )
            )
        shouldThrow<MatrixServerException> {
            cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 24), setOf()))
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
        captureOneTimeKeys[0].keys shouldHaveSize firstSize
        captureOneTimeKeys[0].keys shouldBe captureOneTimeKeys[1].keys
    }

    @Test
    fun `not upload keys when server has 50 one time keys`() = runTest {
        cut.handleOlmKeysChange(OlmKeysChange(mapOf(KeyAlgorithm.SignedCurve25519 to 50), null))
        val captureOneTimeKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.first }
        captureOneTimeKeys should beEmpty()
    }

    @Test
    fun `create and upload fallback key when missing`() = runTest {
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf()))
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf()))

        val captureFallbackKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.second }
        captureFallbackKeys shouldHaveSize 2
        captureFallbackKeys[0].keys shouldHaveSize 1
        val fallbackKey1 = captureFallbackKeys[0].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>()
        fallbackKey1.value.fallback shouldBe true
        captureFallbackKeys[1].keys shouldHaveSize 1
        val fallbackKey2 = captureFallbackKeys[1].keys.first().shouldBeInstanceOf<Key.SignedCurve25519Key>()
        fallbackKey2.value.fallback shouldBe true

        fallbackKey1 shouldNotBe fallbackKey2
    }

    @Test
    fun `not upload fallback key when server has one`() = runTest {
        cut.handleOlmKeysChange(OlmKeysChange(null, setOf(KeyAlgorithm.SignedCurve25519)))
        val captureFallbackKeys = olmEventHandlerRequestHandlerMock.setOneTimeKeysParam.mapNotNull { it.second }
        captureFallbackKeys should beEmpty()
    }

    // ##########################
    // handleOlmEncryptedRoomKeyEventContent
    // ##########################
    @Test
    fun `store new inbound megolm session`() = runTest {
        val outboundSession = groupSession()

        val eventContent = RoomKeyEventContent(
            roomId, outboundSession.sessionId, SessionKeyValue.of(outboundSession.sessionKey),
            EncryptionAlgorithm.Megolm
        )
        val encryptedEvent = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Curve25519KeyValue("BOB_IDEN"),
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
            senderKey shouldBe Curve25519KeyValue("BOB_IDEN")
            senderSigningKey shouldBe Ed25519KeyValue("BOB_SIGN")
        }
    }

    @Test
    fun `store inbound megolm session when existing index higher`() = runTest {
        val outboundSession = groupSession()

        val eventContent = RoomKeyEventContent(
            roomId, outboundSession.sessionId, SessionKeyValue.of(outboundSession.sessionKey),
            EncryptionAlgorithm.Megolm
        )
        val encryptedEvent = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Curve25519KeyValue("BOB_IDEN"),
            ), bob
        )

        olmStoreMock.inboundMegolmSession.put(
            outboundSession.sessionId to roomId,
            StoredInboundMegolmSession(
                senderKey = Curve25519KeyValue("BOB_IDEN"),
                senderSigningKey = Ed25519KeyValue("BOB_SIGN"),
                sessionId = outboundSession.sessionId,
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = false,
                isTrusted = true,
                forwardingCurve25519KeyChain = listOf(),
                pickled = "existing_pickled"
            )
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
            senderKey shouldBe Curve25519KeyValue("BOB_IDEN")
            senderSigningKey shouldBe Ed25519KeyValue("BOB_SIGN")
            pickled shouldNotBe "existing_pickled"
        }
    }

    @Test
    fun `not store inbound megolm session when existing index lower or same`() = runTest {
        val outboundSession = groupSession()

        val eventContent = RoomKeyEventContent(
            roomId, outboundSession.sessionId, SessionKeyValue.of(outboundSession.sessionKey),
            EncryptionAlgorithm.Megolm
        )
        val encryptedEvent = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Curve25519KeyValue("BOB_IDEN"),
            ), bob
        )

        olmStoreMock.inboundMegolmSession.put(
            outboundSession.sessionId to roomId,
            StoredInboundMegolmSession(
                senderKey = Curve25519KeyValue("BOB_IDEN"),
                senderSigningKey = Ed25519KeyValue("BOB_SIGN"),
                sessionId = outboundSession.sessionId,
                roomId = roomId,
                firstKnownIndex = 0,
                hasBeenBackedUp = false,
                isTrusted = true,
                forwardingCurve25519KeyChain = listOf(),
                pickled = "existing_pickled"
            )
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
            senderKey shouldBe Curve25519KeyValue("BOB_IDEN")
            senderSigningKey shouldBe Ed25519KeyValue("BOB_SIGN")
            pickled shouldBe "existing_pickled"
        }
    }

    // ##########################
    // handleMemberEvents
    // ##########################
    @Test
    fun `remove megolm session`() = runTest {
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

    @Test
    fun `update new devices in megolm session`() = runTest {
        olmStoreMock.roomEncryptionAlgorithm[roomId] = EncryptionAlgorithm.Megolm
        olmStoreMock.historyVisibility = HistoryVisibilityEventContent.HistoryVisibility.SHARED
        olmStoreMock.devices.put(
            alice, mapOf(
                "A1" to DeviceKeys(
                    userId = alice,
                    deviceId = "A1",
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf())
                ),
                "A2" to DeviceKeys(
                    userId = alice,
                    deviceId = "A2",
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf())
                )
            )
        )

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
}