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
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.MegolmMessageValue
import net.folivo.trixnity.core.OlmMessageValue
import net.folivo.trixnity.core.SessionKeyValue
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
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.mocks.OlmEncryptionServiceRequestHandlerMock
import net.folivo.trixnity.crypto.mocks.OlmStoreMock
import net.folivo.trixnity.crypto.mocks.SignServiceMock
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.*
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant.Companion.fromEpochMilliseconds

class OlmEncryptionServiceTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDeviceId = "ALICEDEVICE"
    private val bobDeviceId = "BOBDEVICE"
    private val aliceAccount = OlmAccount.create()
    private val bobAccount = OlmAccount.create()

    private val aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.identityKeys.curve25519)
    private val aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.identityKeys.ed25519)
    private val bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.identityKeys.curve25519)
    private val bobEdKey = Ed25519Key(bobDeviceId, bobAccount.identityKeys.ed25519)

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

    init {
        olmStoreMock.devices.put(
            bob, mapOf(
                bobDeviceId to DeviceKeys(
                    userId = bob,
                    deviceId = bobDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(bobCurveKey, bobEdKey))
                )
            )
        )
        olmStoreMock.devices.put(
            alice, mapOf(
                aliceDeviceId to DeviceKeys(
                    userId = alice,
                    deviceId = aliceDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(aliceCurveKey, aliceEdKey))
                )
            )
        )
        olmStoreMock.roomMembers[room] = setOf(alice, bob)
        olmStoreMock.olmAccount.value = aliceAccount.pickle(null)
        mockSignService.returnVerify = VerifyResult.Valid
    }

    private val sendDecryptedOlmEvent = DecryptedOlmEvent(
        content = decryptedOlmEventContent,
        sender = alice,
        senderKeys = keysOf(aliceEdKey.copy(id = null)),
        recipient = bob,
        recipientKeys = keysOf(bobEdKey.copy(id = null))
    )
    private val receiveDecryptedOlmEvent = DecryptedOlmEvent(
        content = decryptedOlmEventContent,
        sender = bob,
        senderKeys = keysOf(bobEdKey.copy(id = null)),
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
    )

    private fun OlmAccount.getOneTimeKey(store: Boolean = false): String {
        generateOneTimeKeys(1)
        return oneTimeKeys.curve25519.values.first()
            .also {
                markKeysAsPublished()
                if (store) olmStoreMock.olmAccount.value = pickle(null)
            }
    }

    private fun mockClaimKeys() {
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(
                bobDeviceId,
                bobAccount.getOneTimeKey(),
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
        freeAfter(
            OlmSession.createInboundFrom(
                account = bobAccount,
                identityKey = aliceCurveKey.value.value,
                oneTimeKeyMessage = encryptedCipherText.body.value
            )
        ) { bobSession ->
            json.decodeFromString(
                decryptedOlmEventSerializer,
                bobSession.decrypt(
                    OlmMessage(
                        encryptedCipherText.body.value,
                        OlmMessageType.INITIAL_PRE_KEY
                    )
                )
            ) shouldBe sendDecryptedOlmEvent
        }

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
        olmStoreMock.devices.put(
            bob, mapOf(
                bobDeviceId to DeviceKeys(
                    userId = bob,
                    deviceId = bobDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                    dehydrated = true
                )
            )
        )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.CrossSigned(false))
        shouldEncryptOlm()
    }

    @Test
    fun `encryptOlm - is Failure when try to encrypt with unverified dehydrated device`() = runTest {
        olmStoreMock.devices.put(
            bob, mapOf(
                bobDeviceId to DeviceKeys(
                    userId = bob,
                    deviceId = bobDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                    dehydrated = true
                )
            )
        )
        olmStoreMock.deviceTrustLevels[bob] = mapOf(bobDeviceId to DeviceTrustLevel.NotCrossSigned)
        val result = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId)
        result.exceptionOrNull() shouldBe EncryptOlmError.DehydratedDeviceNotCrossSigned
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
        freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            val storedOlmSession = freeAfter(
                OlmSession.createInbound(aliceAccount, bobSession.encrypt("first message").cipherText)
            ) { aliceSession ->
                StoredOlmSession(
                    bobCurveKey.value,
                    aliceSession.sessionId, testClock.now(), testClock.now(),
                    aliceSession.pickle(null)
                )
            }

            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
            val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
            assertNotNull(encryptedCipherText)

            encryptedMessage.senderKey shouldBe aliceCurveKey.value
            encryptedCipherText.type shouldBe INITIAL_PRE_KEY

            json.decodeFromString(
                decryptedOlmEventSerializer,
                bobSession.decrypt(
                    OlmMessage(
                        encryptedCipherText.body.value,
                        OlmMessageType.INITIAL_PRE_KEY
                    )
                )
            ) shouldBe sendDecryptedOlmEvent

            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }

    @Test
    fun `decryptOlm - decrypt pre key message from new session`() = runTest {
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
        }
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldHaveSize 1

        // we check, that the one time key cannot be used twice
        shouldThrow<OlmLibraryException> {
            OlmSession.createInboundFrom(
                OlmAccount.unpickle(null, olmStoreMock.olmAccount.value.shouldNotBeNull()),
                bobCurveKey.value.value,
                encryptedMessage.cipherText
            )
        }
    }

    @Test
    fun `decryptOlm - ignore dehydrated device`() = runTest {
        olmStoreMock.devices.put(
            bob, mapOf(
                bobDeviceId to DeviceKeys(
                    userId = bob,
                    deviceId = bobDeviceId,
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(keysOf(bobCurveKey, bobEdKey)),
                    dehydrated = true
                )
            )
        )
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
        }
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.DehydratedDeviceNotAllowed

        olmStoreMock.olmSessions[bobCurveKey.value].shouldBeNull()
    }

    @Test
    fun `decryptOlm - not decrypt pre key message when the 5 last created sessions are not older then 1 hour`() =
        runTest {
            val encryptedMessage = freeAfter(
                OlmSession.createOutbound(
                    bobAccount,
                    aliceCurveKey.value.value,
                    aliceAccount.getOneTimeKey(true)
                )
            ) { bobSession ->
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val existingSessions = (0..4).map { pseudoSessionId ->
                freeAfter(OlmAccount.create()) { dummyAccount ->
                    freeAfter(
                        OlmSession.createOutbound(
                            aliceAccount,
                            dummyAccount.identityKeys.curve25519,
                            dummyAccount.getOneTimeKey()
                        )
                    ) { aliceSession ->
                        StoredOlmSession(
                            bobCurveKey.value,
                            pseudoSessionId.toString(), testClock.now(), testClock.now(),
                            aliceSession.pickle(null)
                        )
                    }
                }
            }.toSet()
            olmStoreMock.olmSessions[bobCurveKey.value] = existingSessions
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.TooManySessions
        }

    @Test
    fun `decryptOlm - fail on ordinary message`() = runTest {
        mockClaimKeys()
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
        }
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), ORDINARY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

        val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
        val encryptedEventContent =
            sendToDeviceEvents.first()[bob]?.get(bobDeviceId)?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()
        val ciphertext = encryptedEventContent?.ciphertext?.get(bobCurveKey.value.value)?.body
        assertNotNull(ciphertext)
        freeAfter(OlmSession.createInbound(bobAccount, ciphertext.value)) { session ->
            json.decodeFromString(
                decryptedOlmEventSerializer,
                session.decrypt(OlmMessage(ciphertext.value, OlmMessageType.INITIAL_PRE_KEY))
            ).content shouldBe DummyEventContent
        }
    }

    @Test
    fun `ddecryptOlm - ecrypt pre key message from stored session`() = runTest {
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value.value,
                bobAccount.getOneTimeKey()
            )
        ) { aliceSession ->
            val firstMessage = aliceSession.encrypt("first message")
            val encryptedMessage = freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                // we do not decrypt the message, so the next is an initial pre key message
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession = StoredOlmSession(
                bobCurveKey.value,
                aliceSession.sessionId, testClock.now(), testClock.now(),
                aliceSession.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }

    @Test
    fun `decryptOlm - decrypt ordinary message`() = runTest {
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value.value,
                bobAccount.getOneTimeKey()
            )
        ) { aliceSession ->
            val firstMessage = aliceSession.encrypt("first message")
            val encryptedMessage = freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession = StoredOlmSession(
                bobCurveKey.value,
                aliceSession.sessionId, testClock.now(),
                testClock.now(),
                aliceSession.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }

    @Test
    fun `decryptOlm - try multiple sessions descended by last used`() = runTest {
        freeAfter(
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value.value, bobAccount.getOneTimeKey()),
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value.value, bobAccount.getOneTimeKey()),
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value.value, bobAccount.getOneTimeKey()),
        ) { aliceSession1, aliceSession2, aliceSession3 ->
            val firstMessage = aliceSession1.encrypt("first message")
            val encryptedMessage = freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession1 = StoredOlmSession(
                bobCurveKey.value,
                aliceSession1.sessionId, testClock.now(), testClock.now(),
                aliceSession1.pickle(null)
            )
            val storedOlmSession2 = StoredOlmSession(
                bobCurveKey.value,
                aliceSession2.sessionId,
                fromEpochMilliseconds(24), testClock.now(),
                aliceSession2.pickle(null)
            )
            val storedOlmSession3 = StoredOlmSession(
                bobCurveKey.value,
                aliceSession3.sessionId, testClock.now(), testClock.now(),
                aliceSession3.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(
                storedOlmSession2,
                storedOlmSession1,
                storedOlmSession3
            )

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldNotContain storedOlmSession1
        }
    }

    @Test
    fun `decryptOlm - not create multiple recovery sessions in short time`() = runTest {
        mockClaimKeys()
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value.value,
                bobAccount.getOneTimeKey()
            )
        ) { aliceSession ->
            val firstMessage = aliceSession.encrypt("first message")
            freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession = StoredOlmSession(
                bobCurveKey.value,
                aliceSession.sessionId, testClock.now(), testClock.now(),
                aliceSession.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            // second recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 1
        }
    }

    @Test
    fun `decryptOlm - create multiple recovery sessions after some time`() = runTest {
        mockClaimKeys()
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value.value,
                bobAccount.getOneTimeKey()
            )
        ) { aliceSession ->
            val firstMessage = aliceSession.encrypt("first message")
            freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession = StoredOlmSession(
                bobCurveKey.value,
                aliceSession.sessionId, testClock.now(), testClock.now(),
                aliceSession.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            delay(11.seconds)

            // second recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue("junk"), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 2
        }
    }

    suspend fun TestScope.handleManipulation(manipulatedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>) {
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value.value,
                bobAccount.getOneTimeKey()
            )
        ) { aliceSession ->
            val firstMessage = aliceSession.encrypt("first message")
            val encryptedMessage = freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, manipulatedOlmEvent))
            }
            val storedOlmSession = StoredOlmSession(
                bobCurveKey.value,
                aliceSession.sessionId, testClock.now(), testClock.now(),
                aliceSession.pickle(null)
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(OlmMessageValue(encryptedMessage.cipherText), ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
        }
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

        val sessionId =
            freeAfter(OlmOutboundGroupSession.unpickle(null, storedOutboundSession.pickled)) { outboundSession ->
                assertSoftly(result) {
                    this.senderKey shouldBe aliceCurveKey.value
                    this.deviceId shouldBe aliceDeviceId
                    this.sessionId shouldBe outboundSession.sessionId
                    this.relatesTo shouldBe this@OlmEncryptionServiceTest.relatesTo.copy(newContent = null)
                }

                val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
                val ciphertext =
                    sendToDeviceEvents.firstOrNull()?.get(bob)?.get(bobDeviceId)
                        ?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()
                        ?.ciphertext?.get(bobCurveKey.value.value)?.body
                assertNotNull(ciphertext)
                freeAfter(OlmSession.createInbound(bobAccount, ciphertext.value)) { session ->
                    assertSoftly(
                        json.decodeFromString(
                            decryptedOlmEventSerializer,
                            session.decrypt(OlmMessage(ciphertext.value, OlmMessageType.INITIAL_PRE_KEY))
                        ).content
                    ) {
                        require(this is RoomKeyEventContent)
                        room shouldBe room
                        sessionId shouldBe outboundSession.sessionId
                        freeAfter(OlmInboundGroupSession.create(sessionKey.value)) { receivedInboundSession ->
                            receivedInboundSession.sessionId shouldBe outboundSession.sessionId
                            receivedInboundSession.firstKnownIndex shouldBe outboundSession.messageIndex - 1
                        }
                    }
                }
                outboundSession.sessionId
            }
        val storedInboundSession = olmStoreMock.inboundMegolmSession[sessionId to room]
        assertNotNull(storedInboundSession)
        assertSoftly(storedInboundSession) {
            sessionId shouldBe sessionId
            senderKey shouldBe aliceCurveKey.value
            room shouldBe room
        }

        freeAfter(OlmInboundGroupSession.unpickle(null, storedInboundSession.pickled)) { inboundSession ->
            json.decodeFromString(
                decryptedMegolmEventSerializer, inboundSession.decrypt(result.ciphertext.value).message
            ) shouldBe decryptedMegolmEvent
        }
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
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            repeat(23) { outboundSession.encrypt("bla") }
            olmStoreMock.outboundMegolmSession[room] =
                StoredOutboundMegolmSession(
                    roomId = room,
                    encryptedMessageCount = 23,
                    newDevices = mapOf(bob to setOf(bobDeviceId)),
                    pickled = outboundSession.pickle(null)
                )
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = aliceCurveKey.value,
                    senderSigningKey = aliceEdKey.value,
                    sessionId = inboundSession.sessionId,
                    roomId = room,
                    firstKnownIndex = inboundSession.firstKnownIndex,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = inboundSession.pickle(null)
                )
            }
        }
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
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey.value,
                    senderSigningKey = bobEdKey.value,
                    sessionId = inboundSession.sessionId,
                    roomId = room,
                    firstKnownIndex = inboundSession.firstKnownIndex,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = inboundSession.pickle(null)
                )
            }
            val ciphertext =
                outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        outboundSession.sessionId,
                        relatesTo = relatesTo
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            )
                .getOrThrow() shouldBe decryptedMegolmEvent.copy(content = decryptedMegolmEvent.content.copy(relatesTo = relatesTo))
            olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)] shouldBe
                    StoredInboundMegolmMessageIndex(
                        outboundSession.sessionId, room, 0, EventId("\$event"), 1234
                    )
        }
    }

    @Test
    fun `decryptMegolm - decrypt megolm event 2`() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            val ciphertext = // encrypted before session saved
                outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey.value,
                    senderSigningKey = bobEdKey.value,
                    sessionId = inboundSession.sessionId,
                    roomId = room,
                    firstKnownIndex = inboundSession.firstKnownIndex,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = inboundSession.pickle(null)
                )
            }
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        outboundSession.sessionId,
                        relatesTo = relatesTo
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            ).exceptionOrNull() shouldBe DecryptMegolmError.MegolmKeyUnknownMessageIndex
        }
    }

    @Test
    fun `decryptMegolm - fail when no keys were send to us`() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            val ciphertext =
                session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        session.sessionId
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            ).exceptionOrNull() shouldBe DecryptMegolmError.MegolmKeyNotFound
        }
    }

    @Test
    fun `decryptMegolm - handle manipulated roomId in megolmEvent`() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey.value,
                    senderSigningKey = bobEdKey.value,
                    sessionId = inboundSession.sessionId,
                    roomId = room,
                    firstKnownIndex = inboundSession.firstKnownIndex,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = inboundSession.pickle(null)
                )
            }
            val ciphertext = outboundSession.encrypt(
                json.encodeToString(
                    decryptedMegolmEventSerializer,
                    decryptedMegolmEvent.copy(roomId = RoomId("!other:server"))
                )
            )
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        outboundSession.sessionId
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
        }
    }

    @Test
    fun `decryptMegolm - handle manipulated message index`() = runTest {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey.value,
                    senderSigningKey = bobEdKey.value,
                    sessionId = inboundSession.sessionId,
                    roomId = room,
                    firstKnownIndex = inboundSession.firstKnownIndex,
                    hasBeenBackedUp = false,
                    isTrusted = true,
                    forwardingCurve25519KeyChain = listOf(),
                    pickled = inboundSession.pickle(null)
                )
            }
            val ciphertext =
                outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)] =
                StoredInboundMegolmMessageIndex(
                    outboundSession.sessionId, room, 0, EventId("\$otherEvent"), 1234
                )
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        outboundSession.sessionId
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
            olmStoreMock.inboundMegolmSessionIndex[Triple(outboundSession.sessionId, room, 0)]
            StoredInboundMegolmMessageIndex(
                outboundSession.sessionId, room, 0, EventId("\$event"), 4321
            )
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        MegolmMessageValue(ciphertext),
                        bobCurveKey.value,
                        bobDeviceId,
                        outboundSession.sessionId
                    ),
                    EventId("\$event"),
                    bob,
                    room,
                    1234
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptMegolmError.ValidationFailed>()
        }
    }
}