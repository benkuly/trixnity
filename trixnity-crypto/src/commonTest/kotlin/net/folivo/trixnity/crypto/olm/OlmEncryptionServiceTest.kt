package net.folivo.trixnity.crypto.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.serialization.ExperimentalSerializationApi
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
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
import net.folivo.trixnity.crypto.mocks.ClockMock
import net.folivo.trixnity.crypto.mocks.OlmEncryptionServiceRequestHandlerMock
import net.folivo.trixnity.crypto.mocks.OlmStoreMock
import net.folivo.trixnity.crypto.mocks.SignServiceMock
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.*
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OlmEncryptionServiceTest : TrixnityBaseTest() {

    val json = createMatrixEventJson()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDeviceId = "ALICEDEVICE"
    val bobDeviceId = "BOBDEVICE"
    lateinit var aliceAccount: OlmAccount
    lateinit var bobAccount: OlmAccount

    lateinit var scope: CoroutineScope

    lateinit var olmStoreMock: OlmStoreMock
    lateinit var olmEncryptionServiceRequestHandlerMock: OlmEncryptionServiceRequestHandlerMock
    lateinit var clockMock: ClockMock
    val mockSignService = SignServiceMock()

    lateinit var cut: OlmEncryptionServiceImpl

    lateinit var aliceCurveKey: Curve25519Key
    lateinit var aliceEdKey: Ed25519Key
    lateinit var bobCurveKey: Curve25519Key
    lateinit var bobEdKey: Ed25519Key

    @OptIn(ExperimentalSerializationApi::class)
    val decryptedOlmEventSerializer = requireNotNull(json.serializersModule.getContextual(DecryptedOlmEvent::class))

    @OptIn(ExperimentalSerializationApi::class)
    val decryptedMegolmEventSerializer = requireNotNull(json.serializersModule.getContextual(DecryptedMegolmEvent::class))

    val decryptedOlmEventContent = RoomKeyEventContent(
        RoomId("room", "server"),
        "sessionId",
        "sessionKey",
        EncryptionAlgorithm.Megolm,
    )
    lateinit var sendDecryptedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>
    lateinit var receiveDecryptedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>

    val relatesTo = RelatesTo.Replace(EventId("$1fancyEvent"), RoomMessageEventContent.TextBased.Text("Hi"))
    val decryptedMegolmEventContent = RoomMessageEventContent.TextBased.Text("*Hi", relatesTo = relatesTo)
    val room = RoomId("room", "server")
    val decryptedMegolmEvent = DecryptedMegolmEvent(decryptedMegolmEventContent, room)

    private suspend fun TestScope.setup() {
        aliceAccount = OlmAccount.create()
        bobAccount = OlmAccount.create()

        aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.identityKeys.curve25519)
        aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.identityKeys.ed25519)
        bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.identityKeys.curve25519)
        bobEdKey = Ed25519Key(bobDeviceId, bobAccount.identityKeys.ed25519)

        scope = backgroundScope

        olmEncryptionServiceRequestHandlerMock = OlmEncryptionServiceRequestHandlerMock()
        olmStoreMock = OlmStoreMock()
        olmStoreMock.ed25519Keys[bob to bobDeviceId] = bobEdKey
        olmStoreMock.curve25519Keys[bob to bobDeviceId] = bobCurveKey
        olmStoreMock.deviceKeys[bob to bobCurveKey.value] = DeviceKeys(
            userId = bob,
            deviceId = bobDeviceId,
            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
            keys = Keys(keysOf(bobCurveKey, bobEdKey))
        )
        olmStoreMock.olmAccount.value = aliceAccount.pickle("")
        olmStoreMock.devices[room] = mapOf(alice to setOf(aliceDeviceId), bob to setOf(bobDeviceId))
        clockMock = ClockMock()

        mockSignService.returnVerify = VerifyResult.Valid

        sendDecryptedOlmEvent = DecryptedOlmEvent(
            content = decryptedOlmEventContent,
            sender = alice,
            senderKeys = keysOf(aliceEdKey.copy(id = null)),
            recipient = bob,
            recipientKeys = keysOf(bobEdKey.copy(id = null))
        )
        receiveDecryptedOlmEvent = DecryptedOlmEvent(
            content = decryptedOlmEventContent,
            sender = bob,
            senderKeys = keysOf(bobEdKey.copy(id = null)),
            recipient = alice,
            recipientKeys = keysOf(aliceEdKey.copy(id = null))
        )

        cut = OlmEncryptionServiceImpl(
            UserInfo(alice, aliceDeviceId, aliceEdKey, aliceCurveKey),
            json,
            olmStoreMock,
            olmEncryptionServiceRequestHandlerMock,
            mockSignService,
            clockMock,
        )
    }

    private fun runTestWithSetup(block: suspend TestScope.() -> Unit) = runTest {
        setup()
        block()
    }

    @AfterTest
    fun free() {
        aliceAccount.free()
        bobAccount.free()
    }

    fun OlmAccount.getOneTimeKey(store: Boolean = false): String {
        generateOneTimeKeys(1)
        return oneTimeKeys.curve25519.values.first()
            .also {
                markKeysAsPublished()
                if (store) olmStoreMock.olmAccount.value = this.pickle("")
            }
    }

    fun mockClaimKeys() {
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

    // #######################
    // encryptOlm
    // #######################
    @Test
    fun `encrypt without stored olm encrypt session`() = runTestWithSetup {
        mockClaimKeys()
        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey.value
        encryptedCipherText.type shouldBe INITIAL_PRE_KEY
        freeAfter(
            OlmSession.createInboundFrom(
                account = bobAccount,
                identityKey = aliceCurveKey.value.value,
                oneTimeKeyMessage = encryptedCipherText.body
            )
        ) { bobSession ->
            json.decodeFromString(
                decryptedOlmEventSerializer,
                bobSession.decrypt(
                    OlmMessage(
                        encryptedCipherText.body,
                        OlmMessageType.INITIAL_PRE_KEY
                    )
                )
            ) shouldBe sendDecryptedOlmEvent
        }

        olmStoreMock.olmSessions[bobCurveKey.value] shouldNotBe null
    }
    @Test
    fun `is failure when one time key is invalid without stored olm encrypt session`() = runTestWithSetup {
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
    fun `encrypt event with stored session`() = runTestWithSetup {
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
                    aliceSession.sessionId,
                    Clock.System.now(),
                    Clock.System.now(),
                    aliceSession.pickle("")
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
                        encryptedCipherText.body,
                        OlmMessageType.INITIAL_PRE_KEY
                    )
                )
            ) shouldBe sendDecryptedOlmEvent

            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    // #######################
    // decryptOlm
    // #######################
    @Test
    fun `decrypt pre key message from new session`() = runTestWithSetup {
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
                        aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldHaveSize 1

        // we check, that the one time key cannot be used twice
        shouldThrow<OlmLibraryException> {
            OlmSession.createInboundFrom(
                OlmAccount.unpickle("", olmStoreMock.olmAccount.value.shouldNotBeNull()),
                bobCurveKey.value.value,
                encryptedMessage.cipherText
            )
        }
    }
    @Test
    fun `not decrypt pre key message when the 5 last created sessions are not older then 1 hour`() = runTestWithSetup {
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
                        pseudoSessionId.toString(),
                        clockMock.now(),
                        clockMock.now(),
                        aliceSession.pickle("")
                    )
                }
            }
        }.toSet()
        olmStoreMock.olmSessions[bobCurveKey.value] = existingSessions
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey.value
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.TooManySessions
    }
    @Test
    fun `fail on ordinary message`() = runTestWithSetup {
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
                        aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
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
        freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
            json.decodeFromString(
                decryptedOlmEventSerializer,
                session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
            ).content shouldBe DummyEventContent
        }
    }
    @Test
    fun `decrypt pre key message from stored session`() = runTestWithSetup {
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
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    @Test
    fun `decrypt ordinary message`() = runTestWithSetup {
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
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    @Test
    fun `try multiple sessions descended by last used`() = runTestWithSetup {
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
                aliceSession1.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession1.pickle("")
            )
            val storedOlmSession2 = StoredOlmSession(
                bobCurveKey.value,
                aliceSession2.sessionId,
                fromEpochMilliseconds(24),
                Clock.System.now(),
                aliceSession2.pickle("")
            )
            val storedOlmSession3 = StoredOlmSession(
                bobCurveKey.value,
                aliceSession3.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession3.pickle("")
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
                            aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey.value].shouldNotBeNull() shouldNotContain storedOlmSession1
        }
    }
    @Test
    fun `not create multiple recovery sessions in short time`() = runTestWithSetup {
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
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo("junk", ORDINARY)
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
                            aliceCurveKey.value.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 1
        }
    }
    @Test
    fun `create multiple recovery sessions after some time`() = runTestWithSetup {
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
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            clockMock.nowValue += 11.seconds
            // second recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 2
        }
    }
    suspend fun handleManipulation(manipulatedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>) {
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
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey.value] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey.value
                    ), bob
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
        }
    }
    @Test
    fun `handle manipulated sender`() = runTestWithSetup {
        handleManipulation(receiveDecryptedOlmEvent.copy(sender = UserId("cedric", "server")))
    }
    @Test
    fun `handle manipulated senderKeys`() = runTestWithSetup {
        handleManipulation(receiveDecryptedOlmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))))
    }
    @Test
    fun `handle manipulated recipient`() = runTestWithSetup {
        handleManipulation(receiveDecryptedOlmEvent.copy(recipient = UserId("cedric", "server")))
    }
    @Test
    fun `handle manipulated recipientKeys`() = runTestWithSetup {
        handleManipulation(
            receiveDecryptedOlmEvent.copy(recipientKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key")))
        )
    }
    // #######################
    // encryptMegolm
    // #######################
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
            freeAfter(OlmOutboundGroupSession.unpickle("", storedOutboundSession.pickled)) { outboundSession ->
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
                freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
                    assertSoftly(
                        json.decodeFromString(
                            decryptedOlmEventSerializer,
                            session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
                        ).content
                    ) {
                        require(this is RoomKeyEventContent)
                        room shouldBe room
                        sessionId shouldBe outboundSession.sessionId
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

        freeAfter(OlmInboundGroupSession.unpickle("", storedInboundSession.pickled)) { inboundSession ->
            json.decodeFromString(
                decryptedMegolmEventSerializer, inboundSession.decrypt(result.ciphertext).message
            ) shouldBe decryptedMegolmEvent
        }
    }
    @Test
    fun `encrypt without stored megolm session`() = runTestWithSetup {
        shouldEncryptMessage(EncryptionEventContent(), 1)
    }
    @Test
    fun `not send room keys when not possible to encrypt them due to missing one time keys`() = runTestWithSetup {
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
            olmStoreMock.outboundMegolmSession[room] =
                StoredOutboundMegolmSession(
                    roomId = room,
                    encryptedMessageCount = 23,
                    newDevices = mapOf(bob to setOf(bobDeviceId)),
                    pickled = outboundSession.pickle("")
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
                    pickled = inboundSession.pickle("")
                )
            }
        }
    }
    @Test
    fun `send megolm sessions to new devices and encrypt`() = runTestWithSetup {
        createExistingOutboundSession()
        shouldEncryptMessage(EncryptionEventContent(), 24)
    }
    @Test
    fun `crete new megolm session when rotation period passed`() = runTestWithSetup {
        olmStoreMock.outboundMegolmSession[room] =
            StoredOutboundMegolmSession(
                roomId = room,
                createdAt = clockMock.nowValue - 24.milliseconds,
                pickled = "is irrelevant"
            )
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMs = 24), 2)
    }
    @Test
    fun `create new megolm session when message count passed`() = runTestWithSetup {
        olmStoreMock.outboundMegolmSession[room] =
            StoredOutboundMegolmSession(
                roomId = room,
                encryptedMessageCount = 24,
                pickled = "is irrelevant"
            )
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMsgs = 24), 25)
    }
    // #######################
    // decryptMegolm
    // #######################
    @Test
    fun `decrypt megolm event 1`() = runTestWithSetup {
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
                    pickled = inboundSession.pickle("")
                )
            }
            val ciphertext =
                outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        ciphertext,
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
    fun `decrypt megolm event 2`() = runTestWithSetup {
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
                    pickled = inboundSession.pickle("")
                )
            }
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        ciphertext,
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
    fun `fail when no keys were send to us`() = runTestWithSetup {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            val ciphertext =
                session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        ciphertext,
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
    fun `handle manipulated roomId in megolmEvent`() = runTestWithSetup {
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
                    pickled = inboundSession.pickle("")
                )
            }
            val ciphertext = outboundSession.encrypt(
                json.encodeToString(
                    decryptedMegolmEventSerializer,
                    decryptedMegolmEvent.copy(roomId = RoomId("other", "server"))
                )
            )
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        ciphertext,
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
    fun `handle manipulated message index`() = runTestWithSetup {
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
                    pickled = inboundSession.pickle("")
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
                        ciphertext,
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
                        ciphertext,
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