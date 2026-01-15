package net.folivo.trixnity.crypto.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.ExperimentalSerializationApi
import net.folivo.trixnity.clientserverapi.model.key.ClaimKeys
import net.folivo.trixnity.core.model.keys.MegolmMessageValue
import net.folivo.trixnity.core.model.keys.OlmMessageValue
import net.folivo.trixnity.core.model.keys.SessionKeyValue
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.DecryptedMegolmEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.ORDINARY
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.olm.Account
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.crypto.invoke
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.mocks.OlmEncryptionServiceRequestHandlerMock
import net.folivo.trixnity.crypto.mocks.OlmStoreMock
import net.folivo.trixnity.crypto.mocks.SignServiceMock
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.*
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant.Companion.fromEpochMilliseconds

class OlmEncryptionServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val account = driver.olm.account
    private val message = driver.olm.message
    private val groupSession = driver.megolm.groupSession
    private val inboundGroupSession = driver.megolm.inboundGroupSession
    private val sessionKey = driver.megolm.sessionKey
    private val megolmMessage = driver.megolm.message

    private val json = createMatrixEventJson()
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDeviceId = "ALICEDEVICE"
    private val bobDeviceId = "BOBDEVICE"

    private val aliceAccount = account()
    private val bobAccount = account()

    private val aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.curve25519Key.base64)
    private val aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.ed25519Key.base64)
    private val aliceDeviceKeys = SignedDeviceKeys(
        DeviceKeys(alice, aliceDeviceId, setOf(EncryptionAlgorithm.Megolm), Keys(keysOf(aliceCurveKey, aliceEdKey))),
    )
    private val bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.curve25519Key.base64)
    private val bobEdKey = Ed25519Key(bobDeviceId, bobAccount.ed25519Key.base64)
    private val bobDeviceKeys = SignedDeviceKeys(
        DeviceKeys(bob, bobDeviceId, setOf(EncryptionAlgorithm.Megolm), Keys(keysOf(bobCurveKey, bobEdKey))),
    )

    private val mockSignService = SignServiceMock()
    private val olmEncryptionServiceRequestHandlerMock = OlmEncryptionServiceRequestHandlerMock()
    private val olmStoreMock = OlmStoreMock()

    @OptIn(ExperimentalSerializationApi::class)
    private val decryptedOlmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(DecryptedOlmEvent::class))

    @OptIn(ExperimentalSerializationApi::class)
    private val decryptedMegolmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(DecryptedMegolmEvent::class))

    private val decryptedOlmEventContent = RoomKeyEventContent(
        RoomId("!room:server"),
        "sessionId",
        SessionKeyValue("sessionKey"),
        EncryptionAlgorithm.Megolm,
    )

    private val relatesTo = RelatesTo.Replace(EventId("$1fancyEvent"), RoomMessageEventContent.TextBased.Text("Hi"))
    private val decryptedMegolmEventContent = RoomMessageEventContent.TextBased.Text("*Hi", relatesTo = relatesTo)
    private val room = RoomId("!room:server")
    private val decryptedMegolmEvent = DecryptedMegolmEvent(decryptedMegolmEventContent, room)

    @BeforeTest
    fun beforeTest() {
        olmStoreMock.devices[bob] = mapOf(
            bobDeviceId to DeviceKeys(
                userId = bob,
                deviceId = bobDeviceId,
                algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keys = Keys(keysOf(bobCurveKey, bobEdKey))
            )
        )
        olmStoreMock.devices[alice] = mapOf(
            aliceDeviceId to DeviceKeys(
                userId = alice,
                deviceId = aliceDeviceId,
                algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keys = Keys(keysOf(aliceCurveKey, aliceEdKey))
            )
        )
        olmStoreMock.roomMembers[room] = setOf(alice, bob)
        olmStoreMock.olmAccount.value = aliceAccount.pickle()
        mockSignService.returnVerify = VerifyResult.Valid
        mockSignService.selfSignedDeviceKeys = aliceDeviceKeys
    }

    private val sendDecryptedOlmEvent = DecryptedOlmEvent(
        content = decryptedOlmEventContent,
        sender = alice,
        senderKeys = keysOf(aliceEdKey.copy(id = null)),
        senderDeviceKeys = aliceDeviceKeys,
        recipient = bob,
        recipientKeys = keysOf(bobEdKey.copy(id = null))
    )
    private val receiveDecryptedOlmEvent = DecryptedOlmEvent(
        content = decryptedOlmEventContent,
        sender = bob,
        senderKeys = keysOf(bobEdKey.copy(id = null)),
        senderDeviceKeys = bobDeviceKeys,
        recipient = alice,
        recipientKeys = keysOf(aliceEdKey.copy(id = null))
    )

    private val cut = OlmEncryptionServiceImpl(
        UserInfo(alice, aliceDeviceId, aliceEdKey, aliceCurveKey),
        json,
        olmStoreMock,
        olmEncryptionServiceRequestHandlerMock,
        mockSignService,
        testScope.testClock,
        driver,
    )

    private fun Account.getOneTimeKey(store: Boolean = false): Curve25519PublicKey {
        generateOneTimeKeys(1)
        return oneTimeKeys.values.first()
            .also {
                markKeysAsPublished()
                if (store) olmStoreMock.olmAccount.value = pickle()
            }
    }

    private fun mockClaimKeys() {
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(
                bobDeviceId,
                bobAccount.getOneTimeKey().base64,
                signatures = mapOf(),
            )
        olmEncryptionServiceRequestHandlerMock.claimKeys = Result.success(
            ClaimKeys.Response(
                emptyMap(),
                mapOf(bob to mapOf(bobDeviceId to keysOf(bobsFakeSignedCurveKey)))
            )
        )
    }

    private suspend fun shouldEncryptOlm() {
        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe INITIAL_PRE_KEY

        val (plaintext, _) = bobAccount.createInboundSession(
            theirIdentityKey = aliceAccount.curve25519Key, preKeyMessage = message.preKey(encryptedCipherText.body)
        )

        json.decodeFromString(
            decryptedOlmEventSerializer,
            plaintext,
        ) shouldBe sendDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value] shouldNotBe null
    }

    @Test
    fun `encryptOlm - encrypt without stored olm encrypt session`() = runTest {
        mockClaimKeys()
        shouldEncryptOlm()
    }


    @Test
    fun `encryptOlm - encrypt for verified dehydrated device`() = runTest {
        mockClaimKeys()
        olmStoreMock.devices[bob] = mapOf(
            bobDeviceId to DeviceKeys(
                userId = bob,
                deviceId = bobDeviceId,
                algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                dehydrated = true
            )
        )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.CrossSigned(false))
        shouldEncryptOlm()
    }

    @Test
    fun `encryptOlm - is Failure when try to encrypt with unverified dehydrated device`() = runTest {
        olmStoreMock.devices[bob] = mapOf(
            bobDeviceId to DeviceKeys(
                userId = bob,
                deviceId = bobDeviceId,
                algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                dehydrated = true
            )
        )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.NotCrossSigned)
        val result = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId)
        result.exceptionOrNull().shouldBeInstanceOf<EncryptOlmError.DehydratedDeviceNotCrossSigned>()
    }

    @Test
    fun `encryptOlm - is failure when one time key is invalid without stored olm encrypt session`() = runTest {
        mockClaimKeys()
        mockSignService.returnVerify = VerifyResult.Invalid("dino")

        cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).exceptionOrNull() shouldBe
                EncryptOlmError.OneTimeKeyVerificationFailed(
                    KeyAlgorithm.SignedCurve25519,
                    VerifyResult.Invalid("dino")
                )
        olmStoreMock.olmSessions.shouldBeEmpty()
    }

    @Test
    fun `encryptOlm - encrypt event with stored session`() = runTest {
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )

        val (_, aliceSession) = aliceAccount.createInboundSession(
            theirIdentityKey = bobAccount.curve25519Key,
            preKeyMessage = bobSession.encrypt("first message") as Message.PreKey,
        )

        val storedOlmSession = StoredOlmSession(
            bobCurveKey.value, aliceSession.sessionId, testClock.now(), testClock.now(), aliceSession.pickle()
        )

        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe ORDINARY

        json.decodeFromString(
            decryptedOlmEventSerializer, bobSession.decrypt(message(encryptedCipherText))
        ) shouldBe sendDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
    }

    @Test
    fun `decryptOlm - decrypt pre key message from new session`() = runTest {
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )

        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldHaveSize 1

        val account = account.fromPickle(
            pickle = olmStoreMock.olmAccount.value.shouldNotBeNull(),
        )

        // we check, that the one time key cannot be used twice
        shouldThrow<CryptoDriverException> {
            account.createInboundSession(
                theirIdentityKey = bobAccount.curve25519Key,
                preKeyMessage = encryptedMessage as Message.PreKey,
            )
        }
    }

    @Test
    fun `decryptOlm - ignore dehydrated device`() = runTest {
        olmStoreMock.devices[bob] = mapOf(
            bobDeviceId to DeviceKeys(
                userId = bob,
                deviceId = bobDeviceId,
                algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                dehydrated = true
            )
        )

        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )

        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.DehydratedDeviceNotAllowed()

        olmStoreMock.olmSessions[bobCurveKey.value].shouldBeNull()
    }

    @Test
    fun `decryptOlm - not decrypt pre key message when the 5 last created sessions are not older then 1 hour`() =
        runTest {
            val bobSession = bobAccount.createOutboundSession(
                identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
            )
            val encryptedMessage = bobSession.encrypt(
                json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
            )

            val existingSessions = (0..4).map { pseudoSessionId ->
                val dummyAccount = account()

                val aliceSession = aliceAccount.createOutboundSession(
                    identityKey = dummyAccount.curve25519Key, oneTimeKey = dummyAccount.getOneTimeKey()
                )

                StoredOlmSession(
                    bobCurveKey.value,
                    pseudoSessionId.toString(),
                    testClock.now(),
                    testClock.now(),
                    aliceSession.pickle()
                )
            }.toSet()

            olmStoreMock.olmSessions[bobCurveKey.value] = existingSessions
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.TooManySessions>()
        }

    @Test
    fun `decryptOlm - fail on ordinary message`() = runTest {
        mockClaimKeys()
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue.of(encryptedMessage), ORDINARY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()

        val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
        val encryptedEventContent =
            sendToDeviceEvents.first()[bob]?.get(bobDeviceId)?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()
        val ciphertext = encryptedEventContent?.ciphertext?.get(bobCurveKey.value.value)?.body
        assertNotNull(ciphertext)

        val (plaintext, _) = bobAccount.createInboundSession(
            preKeyMessage = message.preKey(ciphertext)
        )

        json.decodeFromString(
            decryptedOlmEventSerializer,
            plaintext,
        ).content shouldBe DummyEventContent
    }

    @Test
    fun `decryptOlm - decrypt ordinary message`() = runTest {
        val aliceSession = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )

        val firstMessage = aliceSession.encrypt("first message") as Message.PreKey

        val (_, bobSession) = bobAccount.createInboundSession(
            preKeyMessage = firstMessage
        )

        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        val storedOlmSession = StoredOlmSession(
            bobCurveKey.value, aliceSession.sessionId, testClock.now(), testClock.now(), aliceSession.pickle()
        )

        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent
        olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
    }

    @Test
    fun `decryptOlm - try multiple sessions descended by last used`() = runTest {
        val aliceSession1 = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )
        val aliceSession2 = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )
        val aliceSession3 = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )

        val firstMessage = aliceSession1.encrypt("first message") as Message.PreKey

        val (_, bobSession) = bobAccount.createInboundSession(
            preKeyMessage = firstMessage
        )

        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        val storedOlmSession1 = StoredOlmSession(
            bobCurveKey.value, aliceSession1.sessionId, testClock.now(), testClock.now(), aliceSession1.pickle()
        )
        val storedOlmSession2 = StoredOlmSession(
            bobCurveKey.value,
            aliceSession2.sessionId,
            fromEpochMilliseconds(24),
            testClock.now(),
            aliceSession2.pickle()
        )
        val storedOlmSession3 = StoredOlmSession(
            bobCurveKey.value, aliceSession3.sessionId, testClock.now(), testClock.now(), aliceSession3.pickle()
        )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(
            storedOlmSession2, storedOlmSession1, storedOlmSession3
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent
        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldNotContain storedOlmSession1
    }

    @Test
    fun `decryptOlm - not create multiple recovery sessions in short time`() = runTest {
        mockClaimKeys()
        val aliceSession = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )

        aliceSession.encrypt("first message")

        val storedOlmSession = StoredOlmSession(
            bobCurveKey.value, aliceSession.sessionId, testClock.now(), testClock.now(), aliceSession.pickle()
        )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        // first recovery trigger
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()

        // second recovery trigger
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()

        olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 1
    }

    @Test
    fun `decryptOlm - create multiple recovery sessions after some time`() = runTest {
        mockClaimKeys()
        val aliceSession = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )
        aliceSession.encrypt("first message")

        val storedOlmSession = StoredOlmSession(
            bobCurveKey.value, aliceSession.sessionId, testClock.now(), testClock.now(), aliceSession.pickle()
        )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        // first recovery trigger
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()

        delay(11.seconds)

        // second recovery trigger
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.NoMatchingOlmSessionFound>()

        olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 2
    }

    suspend fun TestScope.handleManipulation(manipulatedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>) {
        val aliceSession = aliceAccount.createOutboundSession(
            identityKey = bobAccount.curve25519Key, oneTimeKey = bobAccount.getOneTimeKey()
        )
        val firstMessage = aliceSession.encrypt("first message") as Message.PreKey

        val storedOlmSession = StoredOlmSession(
            bobCurveKey.value, aliceSession.sessionId, testClock.now(), testClock.now(), aliceSession.pickle()
        )
        olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

        val (_, bobSession) = bobAccount.createInboundSession(
            preKeyMessage = firstMessage
        )

        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, manipulatedOlmEvent)
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ), senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - handle manipulated sender`() = runTest {
        handleManipulation(receiveDecryptedOlmEvent.copy(sender = UserId("cedric", "server")))
    }

    @Test
    fun `decryptOlm - handle manipulated senderKeys`() = runTest {
        handleManipulation(receiveDecryptedOlmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))))
    }

    @Test
    fun `decryptOlm - handle manipulated recipient`() = runTest {
        handleManipulation(receiveDecryptedOlmEvent.copy(recipient = UserId("cedric", "server")))
    }

    @Test
    fun `decryptOlm - handle manipulated recipientKeys`() = runTest {
        handleManipulation(
            receiveDecryptedOlmEvent.copy(recipientKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key")))
        )
    }

    @Test
    fun `decryptOlm - decrypt message with sender device keys in decrypted event`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys userId did not match`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(
                decryptedOlmEventSerializer, receiveDecryptedOlmEvent.copy(
                    senderDeviceKeys = Signed(
                        checkNotNull(receiveDecryptedOlmEvent.senderDeviceKeys?.signed?.copy(userId = alice)),
                        null
                    )
                )
            )
        )

        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys in decrypted event has invalid signature`() = runTest {
        olmStoreMock.devices.clear()
        mockSignService.returnVerify = VerifyResult.Invalid("invalid signature")
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent)
        )

        val result = cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        )
        result.exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys are missing from store and event`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent.copy(senderDeviceKeys = null))
        )

        val result = cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        )
        result.exceptionOrNull() shouldBe DecryptOlmError.KeyNotFound(KeyAlgorithm.Curve25519)
    }

    @Test
    fun `decryptOlm - fail decryption when sender device keys are dehydrated`() = runTest {
        olmStoreMock.devices.clear()
        val bobSession = bobAccount.createOutboundSession(
            identityKey = aliceAccount.curve25519Key, oneTimeKey = aliceAccount.getOneTimeKey(true)
        )
        val encryptedMessage = bobSession.encrypt(
            json.encodeToString(
                decryptedOlmEventSerializer,
                receiveDecryptedOlmEvent.copy(
                    senderDeviceKeys = Signed(
                        checkNotNull(receiveDecryptedOlmEvent.senderDeviceKeys?.signed?.copy(dehydrated = true)),
                        null
                    )
                )
            )
        )

        val result = cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo.of(encryptedMessage)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        )
        result.exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.DehydratedDeviceNotAllowed>()
    }

    suspend fun shouldEncryptMessage(
        settings: EncryptionEventContent,
        expectedMessageCount: Int,
    ) {
        mockClaimKeys()
        val result = cut.encryptMegolm(decryptedMegolmEventContent, room, settings).getOrThrow()

        val storedOutboundSession = olmStoreMock.outboundMegolmSession[room]
        assertNotNull(storedOutboundSession)
        assertSoftly(storedOutboundSession) {
            encryptedMessageCount shouldBe expectedMessageCount
            room shouldBe room
        }

        val outboundSession = groupSession.fromPickle(storedOutboundSession.pickled)

        assertSoftly(result) {
            @Suppress("DEPRECATION")
            this.senderKey shouldBe aliceCurveKey.value
            @Suppress("DEPRECATION")
            this.deviceId shouldBe aliceDeviceId
            this.sessionId shouldBe outboundSession.sessionId
            this.relatesTo shouldBe this@OlmEncryptionServiceTest.relatesTo.copy(newContent = null)
        }

        val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
        val ciphertext = sendToDeviceEvents.firstOrNull()?.get(bob)?.get(bobDeviceId)
            ?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()?.ciphertext?.get(bobCurveKey.value.value)?.body
        assertNotNull(ciphertext)

        val (plaintext, _) = bobAccount.createInboundSession(
            preKeyMessage = message.preKey(ciphertext)
        )

        assertSoftly(
            json.decodeFromString(
                decryptedOlmEventSerializer,
                plaintext,
            ).content
        ) {
            require(this is RoomKeyEventContent)
            room shouldBe room
            sessionId shouldBe outboundSession.sessionId
            val receivedInboundSession = inboundGroupSession(
                sessionKey = sessionKey(sessionKey)
            )
            receivedInboundSession.sessionId shouldBe outboundSession.sessionId
            receivedInboundSession.firstKnownIndex shouldBe outboundSession.messageIndex - 1
        }

        val sessionId = outboundSession.sessionId
        val storedInboundSession = olmStoreMock.inboundMegolmSession[sessionId to room]
        assertNotNull(storedInboundSession)
        assertSoftly(storedInboundSession) {
            sessionId shouldBe sessionId
            senderKey shouldBe aliceCurveKey.value
            room shouldBe room
        }

        val inboundSession = inboundGroupSession.fromPickle(storedInboundSession.pickled)

        json.decodeFromString(
            decryptedMegolmEventSerializer, inboundSession.decrypt(megolmMessage(result.ciphertext)).plaintext
        ) shouldBe decryptedMegolmEvent
    }

    @Test
    fun `encryptMegolm - encrypt without stored megolm session`() = runTest {
        shouldEncryptMessage(EncryptionEventContent(), 1)
    }

    @Test
    fun `encryptMegolm - not send keys to own dehydrated device`() = runTest {
        val aliceDeviceIdDehydrated = aliceDeviceId + "dehydrated"
        olmStoreMock.devices.put(
            alice, mapOf(
                aliceDeviceId to DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey)),
                ),
                aliceDeviceIdDehydrated to DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceIdDehydrated,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey)),
                    dehydrated = true
                )
            )
        )
        shouldEncryptMessage(EncryptionEventContent(), 1)
        olmEncryptionServiceRequestHandlerMock.sendToDeviceParams.firstOrNull().shouldNotBeNull()[alice]
            .shouldBeNull()
    }

    @Test
    fun `encryptMegolm - not send room keys when not possible to encrypt them due to missing one time keys`() =
        runTest {
            olmEncryptionServiceRequestHandlerMock.claimKeys = Result.success(
                ClaimKeys.Response(
                    emptyMap(),
                    mapOf(bob to mapOf(bobDeviceId to keysOf()))
                )
            )

            cut.encryptMegolm(decryptedMegolmEventContent, room, EncryptionEventContent())
            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams.shouldBeEmpty()
            olmEncryptionServiceRequestHandlerMock.claimKeysParams.shouldNotBeEmpty()
        }

    suspend fun createExistingOutboundSession() {
        val outboundSession = groupSession()
        repeat(23) { outboundSession.encrypt("bla") }

        olmStoreMock.outboundMegolmSession[room] = StoredOutboundMegolmSession(
            roomId = room,
            encryptedMessageCount = 23,
            newDevices = mapOf(bob to setOf(bobDeviceId)),
            pickled = outboundSession.pickle()
        )

        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )

        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = aliceCurveKey.value,
            senderSigningKey = aliceEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
    }

    @Test
    fun `encryptMegolm - send megolm sessions to new devices and encrypt`() = runTest {
        createExistingOutboundSession()
        shouldEncryptMessage(EncryptionEventContent(), 24)
    }

    @Test
    fun `encryptMegolm - crete new megolm session when rotation period passed`() = runTest {
        olmStoreMock.outboundMegolmSession[room] =
            StoredOutboundMegolmSession(
                roomId = room, createdAt = testClock.now() - 24.milliseconds,
                pickled = "is irrelevant"
            )
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMs = 24), 2)
    }

    @Test
    fun `encryptMegolm - create new megolm session when message count passed`() = runTest {
        olmStoreMock.outboundMegolmSession[room] =
            StoredOutboundMegolmSession(
                roomId = room,
                encryptedMessageCount = 24,
                pickled = "is irrelevant"
            )
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMsgs = 24), 25)
    }

    @Test
    fun `decryptMegolm - decrypt megolm event 1`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext =
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext),
                    bobCurveKey.value,
                    bobDeviceId,
                    outboundSession.sessionId,
                    relatesTo = relatesTo
                ), EventId("\$event"), bob, room, 1234
            )
        )
            .getOrThrow() shouldBe decryptedMegolmEvent.copy(content = decryptedMegolmEvent.content.copy(relatesTo = relatesTo))

        olmStoreMock.inboundMegolmSessionIndex[Triple(
            outboundSession.sessionId, room, 0
        )] shouldBe StoredInboundMegolmMessageIndex(
            outboundSession.sessionId, room, 0, EventId("\$event"), 1234
        )
    }

    @Test
    fun `decryptMegolm - decrypt megolm event 2`() = runTest {
        val outboundSession = groupSession()
        val ciphertext = // encrypted before session saved
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))

        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext),
                    bobCurveKey.value,
                    bobDeviceId,
                    outboundSession.sessionId,
                    relatesTo = relatesTo
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.MegolmKeyUnknownMessageIndex>()
    }

    @Test
    fun `decryptMegolm - fail when no keys were send to us`() = runTest {
        val session = groupSession()
        val ciphertext = session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, session.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.MegolmKeyNotFound>()
    }

    @Test
    fun `decryptMegolm - handle manipulated roomId in megolmEvent`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext = outboundSession.encrypt(
            json.encodeToString(
                decryptedMegolmEventSerializer, decryptedMegolmEvent.copy(roomId = RoomId("!other:server"))
            )
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
    }

    @Test
    fun `decryptMegolm - handle manipulated message index`() = runTest {
        val outboundSession = groupSession()
        val inboundSession = inboundGroupSession(
            sessionKey = outboundSession.sessionKey
        )
        olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
            senderKey = bobCurveKey.value,
            senderSigningKey = bobEdKey.value,
            sessionId = inboundSession.sessionId,
            roomId = room,
            firstKnownIndex = inboundSession.firstKnownIndex.toLong(),
            hasBeenBackedUp = false,
            isTrusted = true,
            forwardingCurve25519KeyChain = listOf(),
            pickled = inboundSession.pickle()
        )
        val ciphertext =
            outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
        olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)] =
            StoredInboundMegolmMessageIndex(
                outboundSession.sessionId, room, 0, EventId("\$otherEvent"), 1234
            )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
        olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)]
        StoredInboundMegolmMessageIndex(
            outboundSession.sessionId, room, 0, EventId("\$event"), 4321
        )
        cut.decryptMegolm(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    MegolmMessageValue.of(ciphertext), bobCurveKey.value, bobDeviceId, outboundSession.sessionId
                ), EventId("\$event"), bob, room, 1234
            )
        ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
    }
}