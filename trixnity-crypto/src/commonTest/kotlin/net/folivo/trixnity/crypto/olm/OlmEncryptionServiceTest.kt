package net.folivo.trixnity.crypto.olm

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OlmEncryptionServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

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
    val decryptedOlmEventSerializer = json.serializersModule.getContextual(DecryptedOlmEvent::class)
    requireNotNull(decryptedOlmEventSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    val decryptedMegolmEventSerializer = json.serializersModule.getContextual(DecryptedMegolmEvent::class)
    requireNotNull(decryptedMegolmEventSerializer)

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

    beforeEach {
        aliceAccount = OlmAccount.create()
        bobAccount = OlmAccount.create()

        aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.identityKeys.curve25519)
        aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.identityKeys.ed25519)
        bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.identityKeys.curve25519)
        bobEdKey = Ed25519Key(bobDeviceId, bobAccount.identityKeys.ed25519)

        scope = CoroutineScope(Dispatchers.Default)

        olmEncryptionServiceRequestHandlerMock = OlmEncryptionServiceRequestHandlerMock()
        olmStoreMock = OlmStoreMock()
        olmStoreMock.ed25519Keys[bob to bobDeviceId] = bobEdKey
        olmStoreMock.curve25519Keys[bob to bobDeviceId] = bobCurveKey
        olmStoreMock.deviceKeys[bob to bobCurveKey] = DeviceKeys(
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
            senderKeys = keysOf(aliceEdKey.copy(keyId = null)),
            recipient = bob,
            recipientKeys = keysOf(bobEdKey.copy(keyId = null))
        )
        receiveDecryptedOlmEvent = DecryptedOlmEvent(
            content = decryptedOlmEventContent,
            sender = bob,
            senderKeys = keysOf(bobEdKey.copy(keyId = null)),
            recipient = alice,
            recipientKeys = keysOf(aliceEdKey.copy(keyId = null))
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

    afterEach {
        scope.cancel()
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
                mapOf(),
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
    should("encrypt without stored olm encrypt session") {
        mockClaimKeys()
        val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
        val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value]
        assertNotNull(encryptedCipherText)

        encryptedMessage.senderKey shouldBe aliceCurveKey
        encryptedCipherText.type shouldBe INITIAL_PRE_KEY
        freeAfter(
            OlmSession.createInboundFrom(
                account = bobAccount,
                identityKey = aliceCurveKey.value,
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

        olmStoreMock.olmSessions[bobCurveKey] shouldNotBe null
    }
    should("is failure when one time key is invalid without stored olm encrypt session") {
        mockClaimKeys()
        mockSignService.returnVerify = VerifyResult.Invalid("dino")

        cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).exceptionOrNull() shouldBe
                EncryptOlmError.OneTimeKeyVerificationFailed(
                    KeyAlgorithm.SignedCurve25519,
                    VerifyResult.Invalid("dino")
                )
        olmStoreMock.olmSessions.shouldBeEmpty()
    }
    should("encrypt event with stored session") {
        freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            val storedOlmSession = freeAfter(
                OlmSession.createInbound(aliceAccount, bobSession.encrypt("first message").cipherText)
            ) { aliceSession ->
                StoredOlmSession(
                    bobCurveKey,
                    aliceSession.sessionId,
                    Clock.System.now(),
                    Clock.System.now(),
                    aliceSession.pickle("")
                )
            }

            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            val encryptedMessage = cut.encryptOlm(decryptedOlmEventContent, bob, bobDeviceId).getOrThrow()
            val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value]
            assertNotNull(encryptedCipherText)

            encryptedMessage.senderKey shouldBe aliceCurveKey
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

            olmStoreMock.olmSessions[bobCurveKey]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    // #######################
    // decryptOlm
    // #######################
    should("decrypt pre key message from new session") {
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
        }
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey
                ), bob
            )
        ).getOrThrow() shouldBe receiveDecryptedOlmEvent

        olmStoreMock.olmSessions[bobCurveKey].shouldNotBeNull() shouldHaveSize 1

        // we check, that the one time key cannot be used twice
        shouldThrow<OlmLibraryException> {
            OlmSession.createInboundFrom(
                OlmAccount.unpickle("", olmStoreMock.olmAccount.value.shouldNotBeNull()),
                bobCurveKey.value,
                encryptedMessage.cipherText
            )
        }
    }
    should("not decrypt pre key message, when the 5 last created sessions are not older then 1 hour") {
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value,
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
                        bobCurveKey,
                        pseudoSessionId.toString(),
                        Clock.System.now(),
                        Clock.System.now(),
                        aliceSession.pickle("")
                    )
                }
            }
        }.toSet()
        olmStoreMock.olmSessions[bobCurveKey] = existingSessions
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                    ),
                    senderKey = bobCurveKey
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.TooManySessions
    }
    should("fail on ordinary message") {
        mockClaimKeys()
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                aliceCurveKey.value,
                aliceAccount.getOneTimeKey(true)
            )
        ) { bobSession ->
            bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
        }
        cut.decryptOlm(
            ClientEvent.ToDeviceEvent(
                OlmEncryptedToDeviceEventContent(
                    ciphertext = mapOf(
                        aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                    ),
                    senderKey = bobCurveKey
                ), bob
            )
        ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

        val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
        val encryptedEventContent =
            sendToDeviceEvents.first()[bob]?.get(bobDeviceId)?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()
        val ciphertext = encryptedEventContent?.ciphertext?.get(bobCurveKey.value)?.body
        assertNotNull(ciphertext)
        freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
            json.decodeFromString(
                decryptedOlmEventSerializer,
                session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
            ).content shouldBe DummyEventContent
        }
    }
    should("decrypt pre key message from stored session") {
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value,
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
                bobCurveKey,
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    should("decrypt ordinary message") {
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value,
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
                bobCurveKey,
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey]?.firstOrNull().shouldNotBeNull() shouldNotBe storedOlmSession
        }
    }
    should("try multiple sessions descended by last used") {
        freeAfter(
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value, bobAccount.getOneTimeKey()),
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value, bobAccount.getOneTimeKey()),
            OlmSession.createOutbound(aliceAccount, bobCurveKey.value, bobAccount.getOneTimeKey()),
        ) { aliceSession1, aliceSession2, aliceSession3 ->
            val firstMessage = aliceSession1.encrypt("first message")
            val encryptedMessage = freeAfter(
                OlmSession.createInbound(bobAccount, firstMessage.cipherText)
            ) { bobSession ->
                bobSession.decrypt(firstMessage)
                bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, receiveDecryptedOlmEvent))
            }
            val storedOlmSession1 = StoredOlmSession(
                bobCurveKey,
                aliceSession1.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession1.pickle("")
            )
            val storedOlmSession2 = StoredOlmSession(
                bobCurveKey,
                aliceSession2.sessionId,
                fromEpochMilliseconds(24),
                Clock.System.now(),
                aliceSession2.pickle("")
            )
            val storedOlmSession3 = StoredOlmSession(
                bobCurveKey,
                aliceSession3.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession3.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(
                storedOlmSession2,
                storedOlmSession1,
                storedOlmSession3
            )

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).getOrThrow() shouldBe receiveDecryptedOlmEvent
            olmStoreMock.olmSessions[bobCurveKey].shouldNotBeNull() shouldNotContain storedOlmSession1
        }
    }
    should("not create multiple recovery sessions in short time") {
        mockClaimKeys()
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value,
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
                bobCurveKey,
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            // second recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            olmEncryptionServiceRequestHandlerMock.sendToDeviceParams shouldHaveSize 1
        }
    }
    should("create multiple recovery sessions after some time") {
        mockClaimKeys()
        freeAfter(
            OlmSession.createOutbound(
                aliceAccount,
                bobCurveKey.value,
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
                bobCurveKey,
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            // first recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).exceptionOrNull() shouldBe DecryptOlmError.NoMatchingOlmSessionFound

            clockMock.nowValue += 11.seconds
            // second recovery trigger
            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo("junk", ORDINARY)
                        ),
                        senderKey = bobCurveKey
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
                bobCurveKey.value,
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
                bobCurveKey,
                aliceSession.sessionId,
                Clock.System.now(),
                Clock.System.now(),
                aliceSession.pickle("")
            )
            olmStoreMock.olmSessions[bobCurveKey] = setOf(storedOlmSession)

            cut.decryptOlm(
                ClientEvent.ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                )
            ).exceptionOrNull().shouldBeInstanceOf<DecryptOlmError.ValidationFailed>()
        }
    }
    should("handle manipulated sender") {
        handleManipulation(receiveDecryptedOlmEvent.copy(sender = UserId("cedric", "server")))
    }
    should("handle manipulated senderKeys") {
        handleManipulation(receiveDecryptedOlmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))))
    }
    should("handle manipulated recipient") {
        handleManipulation(receiveDecryptedOlmEvent.copy(recipient = UserId("cedric", "server")))
    }
    should("handle manipulated recipientKeys") {
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
                    this.senderKey shouldBe aliceCurveKey
                    this.deviceId shouldBe aliceDeviceId
                    this.sessionId shouldBe outboundSession.sessionId
                    this.relatesTo shouldBe relatesTo.copy(newContent = null)
                }

                val sendToDeviceEvents = olmEncryptionServiceRequestHandlerMock.sendToDeviceParams
                val ciphertext =
                    sendToDeviceEvents.firstOrNull()?.get(bob)?.get(bobDeviceId)
                        ?.shouldBeInstanceOf<OlmEncryptedToDeviceEventContent>()
                        ?.ciphertext?.get(bobCurveKey.value)?.body
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
            senderKey shouldBe aliceCurveKey
            room shouldBe room
        }

        freeAfter(OlmInboundGroupSession.unpickle("", storedInboundSession.pickled)) { inboundSession ->
            json.decodeFromString(
                decryptedMegolmEventSerializer, inboundSession.decrypt(result.ciphertext).message
            ) shouldBe decryptedMegolmEvent
        }
    }
    should("encrypt without stored megolm session") {
        shouldEncryptMessage(EncryptionEventContent(), 1)
    }
    should("not send room keys, when not possible to encrypt them due to missing one time keys") {
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
                    senderKey = aliceCurveKey,
                    senderSigningKey = aliceEdKey,
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
    should("send megolm sessions to new devices and encrypt") {
        createExistingOutboundSession()
        shouldEncryptMessage(EncryptionEventContent(), 24)
    }
    should("crete new megolm session when rotation period passed") {
        olmStoreMock.outboundMegolmSession[room] =
            StoredOutboundMegolmSession(
                roomId = room,
                createdAt = clockMock.nowValue - 24.milliseconds,
                pickled = "is irrelevant"
            )
        shouldEncryptMessage(EncryptionEventContent(rotationPeriodMs = 24), 2)
    }
    should("create new megolm session when message count passed") {
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
    should("decrypt megolm event") {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey,
                    senderSigningKey = bobEdKey,
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
                        bobCurveKey,
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
    should("decrypt megolm event") {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            val ciphertext = // encrypted before session saved
                outboundSession.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey,
                    senderSigningKey = bobEdKey,
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
                        bobCurveKey,
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
    should("fail when no keys were send to us") {
        freeAfter(OlmOutboundGroupSession.create()) { session ->
            val ciphertext =
                session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
            cut.decryptMegolm(
                MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        ciphertext,
                        bobCurveKey,
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
    should("handle manipulated roomId in megolmEvent") {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey,
                    senderSigningKey = bobEdKey,
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
                        bobCurveKey,
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
    should("handle manipulated message index") {
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                olmStoreMock.inboundMegolmSession[outboundSession.sessionId to room] = StoredInboundMegolmSession(
                    senderKey = bobCurveKey,
                    senderSigningKey = bobEdKey,
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
                        bobCurveKey,
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
                        bobCurveKey,
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