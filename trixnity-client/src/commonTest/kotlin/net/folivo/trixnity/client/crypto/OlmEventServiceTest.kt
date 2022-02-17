package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.minus
import kotlinx.serialization.ExperimentalSerializationApi
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeysResponse
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.DummyEventContent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.ORDINARY
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class OlmEventServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixJson()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDeviceId = "ALICEDEVICE"
    val bobDeviceId = "BOBDEVICE"
    val aliceAccount = OlmAccount.create()
    val bobAccount = OlmAccount.create()
    val relatesTo = RelatesTo.Reference(EventId("$1fancyEvent"))

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope

    val api = mockk<MatrixClientServerApiClient>()
    val signService = mockk<OlmSignService>()

    lateinit var cut: OlmEventService

    val aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.identityKeys.curve25519)
    val aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.identityKeys.ed25519)
    val bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.identityKeys.curve25519)
    val bobEdKey = Ed25519Key(bobDeviceId, bobAccount.identityKeys.ed25519)

    @OptIn(ExperimentalSerializationApi::class) val olmEventSerializer =
        json.serializersModule.getContextual(OlmEvent::class)
    @OptIn(ExperimentalSerializationApi::class) val megolmEventSerializer =
        json.serializersModule.getContextual(MegolmEvent::class)
    requireNotNull(olmEventSerializer)
    requireNotNull(megolmEventSerializer)

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        store.keys.updateDeviceKeys(bob) {
            mapOf(
                bobDeviceId to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            userId = bob,
                            deviceId = bobDeviceId,
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(keysOf(bobCurveKey, bobEdKey))
                        ), mapOf(bob to keysOf(Ed25519Key("BOBD", "bobEdKey")))
                    ), KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        bobAccount.generateOneTimeKeys(1)
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(bobDeviceId, bobAccount.oneTimeKeys.curve25519.values.first(), mapOf())
        coEvery {
            api.keys.claimKeys(mapOf(bob to mapOf(bobDeviceId to KeyAlgorithm.SignedCurve25519)))
        } returns Result.success(
            ClaimKeysResponse(
                emptyMap(),
                mapOf(bob to mapOf(bobDeviceId to keysOf(bobsFakeSignedCurveKey)))
            )
        )
        bobAccount.markKeysAsPublished()
        coEvery { signService.verify(any<Key.SignedCurve25519Key>(), any()) } returns VerifyResult.Valid
        coEvery { api.users.sendToDevice<OlmEncryptedEventContent>(any(), any(), any()) } returns Result.success(Unit)

        cut = OlmEventService(
            "",
            alice,
            aliceDeviceId,
            aliceEdKey,
            aliceCurveKey,
            json,
            aliceAccount,
            store,
            api,
            signService
        )
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    afterSpec {
        aliceAccount.free()
        bobAccount.free()
    }

    context(OlmEventService::encryptOlm.name) {
        val eventContent = RoomKeyEventContent(
            RoomId("room", "server"),
            "sessionId",
            "sessionKey",
            EncryptionAlgorithm.Megolm,
        )
        val olmEvent = OlmEvent(
            content = eventContent,
            sender = alice,
            senderKeys = keysOf(aliceEdKey.copy(keyId = null)),
            recipient = bob,
            recipientKeys = keysOf(bobEdKey.copy(keyId = null))
        )
        context("without stored session") {
            should("encrypt") {
                val encryptedMessage = cut.encryptOlm(eventContent, bob, bobDeviceId)
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
                        olmEventSerializer,
                        bobSession.decrypt(OlmMessage(encryptedCipherText.body, OlmMessageType.INITIAL_PRE_KEY))
                    ) shouldBe olmEvent
                }
                store.olm.getOlmSessions(bobCurveKey)!! shouldHaveSize 1
            }
            should("throw exception when one time key is invalid") {
                coEvery {
                    signService.verify(
                        any<Key.SignedCurve25519Key>(),
                        any()
                    )
                } returns VerifyResult.Invalid("dino")

                shouldThrow<KeyException.KeyVerificationFailedException> {
                    cut.encryptOlm(eventContent, bob, bobDeviceId).ciphertext.entries.first().value
                }.message shouldBe "dino"
                store.olm.getOlmSessions(bobCurveKey) should beNull()
            }
        }
        context("with stored session") {
            should("encrypt event with stored session") {
                aliceAccount.generateOneTimeKeys(1)
                freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.oneTimeKeys.curve25519.values.first()
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
                    store.olm.updateOlmSessions(bobCurveKey) { setOf(storedOlmSession) }

                    val encryptedMessage = cut.encryptOlm(eventContent, bob, bobDeviceId)
                    val encryptedCipherText = encryptedMessage.ciphertext[bobCurveKey.value]
                    assertNotNull(encryptedCipherText)

                    encryptedMessage.senderKey shouldBe aliceCurveKey
                    encryptedCipherText.type shouldBe INITIAL_PRE_KEY

                    json.decodeFromString(
                        olmEventSerializer,
                        bobSession.decrypt(
                            OlmMessage(
                                encryptedCipherText.body,
                                OlmMessageType.INITIAL_PRE_KEY
                            )
                        )
                    ) shouldBe olmEvent
                    store.olm.getOlmSessions(bobCurveKey)!! shouldHaveSize 1
                    store.olm.getOlmSessions(bobCurveKey)?.first() shouldNotBe storedOlmSession
                }
            }
        }
    }
    context(OlmEventService::decryptOlm.name) {
        val eventContent = RoomKeyEventContent(
            RoomId("room", "server"),
            "sessionId",
            "sessionKey",
            EncryptionAlgorithm.Megolm
        )
        val olmEvent = OlmEvent(
            content = eventContent,
            sender = bob,
            senderKeys = keysOf(bobEdKey),
            recipient = alice,
            recipientKeys = keysOf(aliceEdKey)
        )
        beforeTest {
            aliceAccount.generateOneTimeKeys(1)
        }
        context("without stored session") {
            should("decrypt pre key message") {
                val encryptedMessage = freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
                }
                cut.decryptOlm(
                    OlmEncryptedEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                ) shouldBe olmEvent
                store.olm.getOlmSessions(bobCurveKey)!! shouldHaveSize 1

                // we check, that the one time key cannot be used twice
                shouldThrow<OlmLibraryException> {
                    OlmSession.createInboundFrom(aliceAccount, bobCurveKey.value, encryptedMessage.cipherText)
                }
            }
            should("not decrypt pre key message, when the 5 last created sessions are not older then 1 hour") {
                val encryptedMessage = freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
                }
                repeat(5) { pseudoSessionId ->
                    freeAfter(OlmAccount.create()) { dummyAccount ->
                        dummyAccount.generateOneTimeKeys(1)
                        freeAfter(
                            OlmSession.createOutbound(
                                aliceAccount,
                                dummyAccount.identityKeys.curve25519,
                                dummyAccount.oneTimeKeys.curve25519.values.first()
                            )
                        ) { aliceSession ->
                            val storedOlmSession = StoredOlmSession(
                                bobCurveKey,
                                pseudoSessionId.toString(),
                                Clock.System.now(),
                                Clock.System.now(),
                                aliceSession.pickle("")
                            )
                            store.olm.updateOlmSessions(bobCurveKey) {
                                it?.plus(storedOlmSession) ?: setOf(
                                    storedOlmSession
                                )
                            }
                        }
                    }
                }
                shouldThrow<SessionException.PreventToManySessions> {
                    cut.decryptOlm(
                        OlmEncryptedEventContent(
                            ciphertext = mapOf(
                                aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                            ),
                            senderKey = bobCurveKey
                        ), bob
                    )
                }
            }
            should("throw on ordinary message") {
                val encryptedMessage = freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
                }
                shouldThrow<SessionException.CouldNotDecrypt> {
                    cut.decryptOlm(
                        OlmEncryptedEventContent(
                            ciphertext = mapOf(
                                aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                            ),
                            senderKey = bobCurveKey
                        ), bob
                    )
                }
                val sendToDeviceEvents = slot<Map<UserId, Map<String, OlmEncryptedEventContent>>>()
                coVerify {
                    api.users.sendToDevice(capture(sendToDeviceEvents), any(), any())
                }

                val ciphertext =
                    sendToDeviceEvents.captured[bob]?.get(bobDeviceId)?.ciphertext?.get(bobCurveKey.value)?.body
                assertNotNull(ciphertext)
                freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
                    json.decodeFromString(
                        olmEventSerializer,
                        session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
                    ).content shouldBe DummyEventContent
                }
            }
        }
        context("with stored session") {
            should("decrypt pre key message") {
                bobAccount.generateOneTimeKeys(1)
                freeAfter(
                    OlmSession.createOutbound(
                        aliceAccount,
                        bobCurveKey.value,
                        bobAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { aliceSession ->
                    val firstMessage = aliceSession.encrypt("first message")
                    val encryptedMessage = freeAfter(
                        OlmSession.createInbound(bobAccount, firstMessage.cipherText)
                    ) { bobSession ->
                        // we do not decrypt the message, so the next is an initial pre key message
                        bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
                    }
                    val storedOlmSession = StoredOlmSession(
                        bobCurveKey,
                        aliceSession.sessionId,
                        Clock.System.now(),
                        Clock.System.now(),
                        aliceSession.pickle("")
                    )
                    store.olm.updateOlmSessions(bobCurveKey) { setOf(storedOlmSession) }

                    cut.decryptOlm(
                        OlmEncryptedEventContent(
                            ciphertext = mapOf(
                                aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                            ),
                            senderKey = bobCurveKey
                        ), bob
                    ) shouldBe olmEvent
                    store.olm.getOlmSessions(bobCurveKey)?.first() shouldNotBe storedOlmSession
                }
            }
            should("decrypt ordinary message") {
                bobAccount.generateOneTimeKeys(1)
                freeAfter(
                    OlmSession.createOutbound(
                        aliceAccount,
                        bobCurveKey.value,
                        bobAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { aliceSession ->
                    val firstMessage = aliceSession.encrypt("first message")
                    val encryptedMessage = freeAfter(
                        OlmSession.createInbound(bobAccount, firstMessage.cipherText)
                    ) { bobSession ->
                        bobSession.decrypt(firstMessage)
                        bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
                    }
                    val storedOlmSession = StoredOlmSession(
                        bobCurveKey,
                        aliceSession.sessionId,
                        Clock.System.now(),
                        Clock.System.now(),
                        aliceSession.pickle("")
                    )
                    store.olm.updateOlmSessions(bobCurveKey) { setOf(storedOlmSession) }

                    cut.decryptOlm(
                        OlmEncryptedEventContent(
                            ciphertext = mapOf(
                                aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                            ),
                            senderKey = bobCurveKey
                        ), bob
                    ) shouldBe olmEvent
                    store.olm.getOlmSessions(bobCurveKey)?.first() shouldNotBe storedOlmSession
                }
            }
            should("try multiple sessions descended by last used") {
                bobAccount.generateOneTimeKeys(3)
                val oneTimeKeys = bobAccount.oneTimeKeys.curve25519.values.toList()
                freeAfter(
                    OlmSession.createOutbound(aliceAccount, bobCurveKey.value, oneTimeKeys[0]),
                    OlmSession.createOutbound(aliceAccount, bobCurveKey.value, oneTimeKeys[1]),
                    OlmSession.createOutbound(aliceAccount, bobCurveKey.value, oneTimeKeys[2]),
                ) { aliceSession1, aliceSession2, aliceSession3 ->
                    val firstMessage = aliceSession1.encrypt("first message")
                    val encryptedMessage = freeAfter(
                        OlmSession.createInbound(bobAccount, firstMessage.cipherText)
                    ) { bobSession ->
                        bobSession.decrypt(firstMessage)
                        bobSession.encrypt(json.encodeToString(olmEventSerializer, olmEvent))
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
                    store.olm.updateOlmSessions(bobCurveKey) {
                        setOf(
                            storedOlmSession2,
                            storedOlmSession1,
                            storedOlmSession3
                        )
                    }

                    cut.decryptOlm(
                        OlmEncryptedEventContent(
                            ciphertext = mapOf(
                                aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                            ),
                            senderKey = bobCurveKey
                        ), bob
                    ) shouldBe olmEvent
                    store.olm.getOlmSessions(bobCurveKey)!! shouldNotContain storedOlmSession1
                }
            }
        }
        context("handle olm event with manipulated") {
            withData(
                mapOf(
                    "sender" to olmEvent.copy(sender = UserId("cedric", "server")),
                    "senderKeys" to olmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))),
                    "recipient" to olmEvent.copy(recipient = UserId("cedric", "server")),
                    "recipientKeys" to olmEvent.copy(recipientKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key")))
                )
            ) { manipulatedOlmEvent ->
                val job1 = launch {
                    store.keys.outdatedKeys.first { it.isNotEmpty() }
                    store.keys.outdatedKeys.value = setOf()
                }
                bobAccount.generateOneTimeKeys(1)
                freeAfter(
                    OlmSession.createOutbound(
                        aliceAccount,
                        bobCurveKey.value,
                        bobAccount.oneTimeKeys.curve25519.values.first()
                    )
                ) { aliceSession ->
                    val firstMessage = aliceSession.encrypt("first message")
                    val encryptedMessage = freeAfter(
                        OlmSession.createInbound(bobAccount, firstMessage.cipherText)
                    ) { bobSession ->
                        bobSession.decrypt(firstMessage)
                        bobSession.encrypt(json.encodeToString(olmEventSerializer, manipulatedOlmEvent))
                    }
                    val storedOlmSession = StoredOlmSession(
                        bobCurveKey,
                        aliceSession.sessionId,
                        Clock.System.now(),
                        Clock.System.now(),
                        aliceSession.pickle("")
                    )
                    store.olm.updateOlmSessions(bobCurveKey) { setOf(storedOlmSession) }

                    shouldThrow<DecryptionException> {
                        cut.decryptOlm(
                            OlmEncryptedEventContent(
                                ciphertext = mapOf(
                                    aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, ORDINARY)
                                ),
                                senderKey = bobCurveKey
                            ), bob
                        )
                    }
                }
                job1.cancel()
            }
        }
    }
    context(OlmEventService::encryptMegolm.name) {
        val eventContent = TextMessageEventContent("Hi", relatesTo = relatesTo)
        val room = RoomId("room", "server")
        val megolmEvent = MegolmEvent(eventContent, room)
        beforeTest {
            store.room.update(room) { Room(room, membership = JOIN, membersLoaded = true) }
            listOf(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                ),
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event2"),
                    bob,
                    room,
                    1235,
                    stateKey = bob.full
                )
            ).forEach { store.roomState.update(it) }
        }
        suspend fun ShouldSpecContainerScope.testEncryption(
            settings: EncryptionEventContent,
            expectedMessageCount: Int
        ) {
            should("encrypt message") {
                store.keys.outdatedKeys.value = setOf(bob)
                val asyncResult = async { cut.encryptMegolm(eventContent, room, settings) }
                store.keys.outdatedKeys.subscriptionCount.takeWhile { it == 1 }.take(1).collect()
                asyncResult.isActive shouldBe true
                store.keys.outdatedKeys.value = setOf()
                val result = asyncResult.await()

                val storedOutboundSession = store.olm.getOutboundMegolmSession(room)
                assertNotNull(storedOutboundSession)
                assertSoftly(storedOutboundSession) {
                    encryptedMessageCount shouldBe expectedMessageCount
                    roomId shouldBe room
                }

                freeAfter(OlmOutboundGroupSession.unpickle("", storedOutboundSession.pickled)) { outboundSession ->
                    assertSoftly(result) {
                        senderKey shouldBe aliceCurveKey
                        deviceId shouldBe aliceDeviceId
                        sessionId shouldBe outboundSession.sessionId
                        this.relatesTo shouldBe relatesTo
                    }

                    val sendToDeviceEvents = slot<Map<UserId, Map<String, OlmEncryptedEventContent>>>()
                    coVerify {
                        api.users.sendToDevice(capture(sendToDeviceEvents), any(), any())
                    }

                    val ciphertext =
                        sendToDeviceEvents.captured[bob]?.get(bobDeviceId)?.ciphertext?.get(bobCurveKey.value)?.body
                    assertNotNull(ciphertext)
                    freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
                        assertSoftly(
                            json.decodeFromString(
                                olmEventSerializer,
                                session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
                            ).content
                        ) {
                            require(this is RoomKeyEventContent)
                            roomId shouldBe room
                            sessionId shouldBe outboundSession.sessionId
                        }
                    }

                    val storedInboundSession =
                        store.olm.getInboundMegolmSession(
                            aliceCurveKey,
                            outboundSession.sessionId,
                            room,
                            this
                        ).value
                    assertNotNull(storedInboundSession)
                    assertSoftly(storedInboundSession) {
                        sessionId shouldBe outboundSession.sessionId
                        senderKey shouldBe aliceCurveKey
                        roomId shouldBe room
                    }

                    freeAfter(OlmInboundGroupSession.unpickle("", storedInboundSession.pickled)) { inboundSession ->
                        json.decodeFromString(
                            megolmEventSerializer, inboundSession.decrypt(result.ciphertext).message
                        ) shouldBe megolmEvent
                    }
                }
            }
        }
        context("without stored session") {
            testEncryption(EncryptionEventContent(), 1)
            should("not send room keys, when not possible to encrypt them due to missing one time keys") {
                val otherRoom = RoomId("otherRoom", "server")
                store.room.update(otherRoom) { Room(otherRoom, membership = JOIN, membersLoaded = true) }
                val cedric = UserId("cedric", "server")
                listOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event1"),
                        alice,
                        otherRoom,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event2"),
                        cedric,
                        otherRoom,
                        1235,
                        stateKey = cedric.full
                    )
                ).forEach { store.roomState.update(it) }

                store.keys.outdatedKeys.value = setOf(cedric)
                val asyncResult = async { cut.encryptMegolm(eventContent, otherRoom, EncryptionEventContent()) }
                store.keys.outdatedKeys.subscriptionCount.takeWhile { it == 1 }.take(1).collect()
                asyncResult.isActive shouldBe true
                store.keys.outdatedKeys.value = setOf()
                asyncResult.await()

                coVerify(exactly = 0) {
                    api.users.sendToDevice<OlmEncryptedEventContent>(any(), any(), any())
                }
            }
            should("wait that room members are loaded") {
                store.room.update(room) { Room(room, membership = JOIN, membersLoaded = false) }
                store.room.get(room).first { it?.membersLoaded == false }
                val cedric = UserId("cedric", "server")
                listOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event1"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        EventId("\$event2"),
                        cedric,
                        room,
                        1235,
                        stateKey = cedric.full
                    )
                ).forEach { store.roomState.update(it) }

                val asyncResult = async { cut.encryptMegolm(eventContent, room, EncryptionEventContent()) }
                continually(200.milliseconds) {
                    asyncResult.isActive shouldBe true
                }
                store.room.update(room) { it?.copy(membersLoaded = true) }
                asyncResult.await()
            }
        }
        context("with stored session") {
            context("send sessions to new devices and encrypt") {
                beforeTest {
                    freeAfter(OlmOutboundGroupSession.create()) { session ->
                        store.olm.updateOutboundMegolmSession(room) {
                            StoredOutboundMegolmSession(
                                roomId = room,
                                encryptedMessageCount = 23,
                                newDevices = mapOf(bob to setOf(bobDeviceId)),
                                pickled = session.pickle("")
                            )
                        }
                        store.olm.storeTrustedInboundMegolmSession(
                            roomId = room,
                            senderKey = aliceCurveKey,
                            senderSigningKey = aliceEdKey,
                            sessionId = session.sessionId,
                            sessionKey = session.sessionKey,
                            pickleKey = ""
                        )
                    }
                }
                testEncryption(EncryptionEventContent(), 24)
            }
            context("when rotation period passed") {
                beforeTest {
                    store.olm.updateOutboundMegolmSession(room) {
                        StoredOutboundMegolmSession(
                            roomId = room,
                            createdAt = Clock.System.now().minus(24, DateTimeUnit.MILLISECOND),
                            pickled = "is irrelevant"
                        )
                    }
                }
                testEncryption(EncryptionEventContent(rotationPeriodMs = 24), 2)
            }
            context("when message count passed") {
                beforeTest {
                    store.olm.updateOutboundMegolmSession(room) {
                        StoredOutboundMegolmSession(
                            roomId = room,
                            encryptedMessageCount = 24,
                            pickled = "is irrelevant"
                        )
                    }
                }
                testEncryption(EncryptionEventContent(rotationPeriodMsgs = 24), 25)
            }
        }
    }
    context(OlmEventService::decryptMegolm.name) {
        val eventContent = TextMessageEventContent("Hi")
        val room = RoomId("room", "server")
        val megolmEvent = MegolmEvent(eventContent, room)
        should("decrypt megolm event") {
            freeAfter(OlmOutboundGroupSession.create()) { session ->
                store.olm.storeTrustedInboundMegolmSession(
                    roomId = room,
                    senderKey = bobCurveKey,
                    senderSigningKey = bobEdKey,
                    sessionId = session.sessionId,
                    sessionKey = session.sessionKey,
                    pickleKey = ""
                )
                val ciphertext = session.encrypt(json.encodeToString(megolmEventSerializer, megolmEvent))
                cut.decryptMegolm(
                    MessageEvent(
                        MegolmEncryptedEventContent(
                            ciphertext,
                            bobCurveKey,
                            bobDeviceId,
                            session.sessionId,
                            relatesTo = relatesTo
                        ),
                        EventId("\$event"),
                        bob,
                        room,
                        1234
                    )
                ) shouldBe megolmEvent.copy(content = megolmEvent.content.copy(relatesTo = relatesTo))
                store.olm.updateInboundMegolmMessageIndex(bobCurveKey, session.sessionId, room, 0) {
                    it shouldBe StoredInboundMegolmMessageIndex(
                        bobCurveKey, session.sessionId, room, 0, EventId("\$event"), 1234
                    )
                    it
                }
            }
        }
        should("throw when no keys were send to us") {
            freeAfter(OlmOutboundGroupSession.create()) { session ->
                val ciphertext = session.encrypt(json.encodeToString(megolmEventSerializer, megolmEvent))
                shouldThrow<DecryptionException> {
                    cut.decryptMegolm(
                        MessageEvent(
                            MegolmEncryptedEventContent(
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
                    )
                }
            }
        }
        context("manipulation") {
            should("handle manipulated roomId in megolmEvent") {
                freeAfter(OlmOutboundGroupSession.create()) { session ->
                    store.olm.storeTrustedInboundMegolmSession(
                        roomId = room,
                        senderKey = bobCurveKey,
                        senderSigningKey = bobEdKey,
                        sessionId = session.sessionId,
                        sessionKey = session.sessionKey,
                        pickleKey = ""
                    )
                    val ciphertext = session.encrypt(
                        json.encodeToString(
                            megolmEventSerializer,
                            megolmEvent.copy(roomId = RoomId("other", "server"))
                        )
                    )
                    shouldThrow<DecryptionException> {
                        cut.decryptMegolm(
                            MessageEvent(
                                MegolmEncryptedEventContent(
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
                        )
                    }
                }
            }
            should("handle manipulated message index") {
                freeAfter(OlmOutboundGroupSession.create()) { session ->
                    store.olm.storeTrustedInboundMegolmSession(
                        roomId = room,
                        senderKey = bobCurveKey,
                        senderSigningKey = bobEdKey,
                        sessionId = session.sessionId,
                        sessionKey = session.sessionKey,
                        pickleKey = ""
                    )
                    val ciphertext = session.encrypt(json.encodeToString(megolmEventSerializer, megolmEvent))
                    store.olm.updateInboundMegolmMessageIndex(bobCurveKey, session.sessionId, room, 0) {
                        StoredInboundMegolmMessageIndex(
                            bobCurveKey, session.sessionId, room, 0, EventId("\$otherEvent"), 1234
                        )
                    }
                    shouldThrow<DecryptionException> {
                        cut.decryptMegolm(
                            MessageEvent(
                                MegolmEncryptedEventContent(
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
                        )
                    }
                    store.olm.updateInboundMegolmMessageIndex(bobCurveKey, session.sessionId, room, 0) {
                        StoredInboundMegolmMessageIndex(
                            bobCurveKey, session.sessionId, room, 0, EventId("\$event"), 4321
                        )
                    }
                    shouldThrow<DecryptionException> {
                        cut.decryptMegolm(
                            MessageEvent(
                                MegolmEncryptedEventContent(
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
                        )
                    }
                }
            }
            should("handle manipulated senderKey in event content") {
                freeAfter(OlmOutboundGroupSession.create()) { session ->
                    store.olm.storeTrustedInboundMegolmSession(
                        roomId = room,
                        senderKey = Curve25519Key(null, "cedrics curve key"),
                        senderSigningKey = Ed25519Key(null, "cedric ed key"),
                        sessionId = session.sessionId,
                        sessionKey = session.sessionKey,
                        pickleKey = ""
                    )
                    val ciphertext = session.encrypt(json.encodeToString(megolmEventSerializer, megolmEvent))
                    shouldThrow<DecryptionException> {
                        cut.decryptMegolm(
                            MessageEvent(
                                MegolmEncryptedEventContent(
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
                        )
                    }
                }
            }
        }
    }
}