package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
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
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.OlmSignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
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
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.olm.OlmMessage.OlmMessageType
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class OlmEventServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDeviceId = "ALICEDEVICE"
    val bobDeviceId = "BOBDEVICE"
    lateinit var aliceAccount: OlmAccount
    lateinit var bobAccount: OlmAccount
    val relatesTo = RelatesTo.Reference(EventId("$1fancyEvent"))

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope

    val signService = OlmSignServiceMock()
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: OlmEventService

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

    beforeEach {
        aliceAccount = OlmAccount.create()
        bobAccount = OlmAccount.create()

        aliceCurveKey = Curve25519Key(aliceDeviceId, aliceAccount.identityKeys.curve25519)
        aliceEdKey = Ed25519Key(aliceDeviceId, aliceAccount.identityKeys.ed25519)
        bobCurveKey = Curve25519Key(bobDeviceId, bobAccount.identityKeys.curve25519)
        bobEdKey = Ed25519Key(bobDeviceId, bobAccount.identityKeys.ed25519)

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
        signService.returnVerify = VerifyResult.Valid
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig

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

    afterEach {
        storeScope.cancel()
        aliceAccount.free()
        bobAccount.free()
    }
    fun OlmAccount.getOneTimeKey(): String {
        generateOneTimeKeys(1)
        return oneTimeKeys.curve25519.values.first()
            .also { markKeysAsPublished() }
    }

    fun MockEngineConfig.claimKeysEndpoint() {
        val bobsFakeSignedCurveKey =
            Key.SignedCurve25519Key(bobDeviceId, bobAccount.getOneTimeKey(), mapOf())
        matrixJsonEndpoint(json, mappings, ClaimKeys()) {
            it.oneTimeKeys shouldBe (mapOf(bob to mapOf(bobDeviceId to KeyAlgorithm.SignedCurve25519)))
            ClaimKeys.Response(
                emptyMap(),
                mapOf(bob to mapOf(bobDeviceId to keysOf(bobsFakeSignedCurveKey)))
            )
        }
    }


    context(OlmEventService::handleOlmEncryptedToDeviceEvents.name) {
        context("exceptions") {
            val event = Event.ToDeviceEvent(
                OlmEncryptedEventContent(
                    mapOf(), Curve25519Key(null, "")
                ),
                UserId("sender", "server")
            )
            should("catch exceptions") {
                cut.handleOlmEncryptedToDeviceEvents(event)
            }
        }
        should("emit decrypted events") {
            freeAfter(OlmUtility.create()) { olmUtility ->
                val bobStore = InMemoryStore(storeScope).apply { init() }
                val bobOlmService =
                    OlmService("", bob, bobDeviceId, bobStore, api, json, bobAccount, olmUtility)
                store.olm.storeAccount(aliceAccount, "")
                val aliceSignService = OlmSignService(alice, aliceDeviceId, json, store, aliceAccount, olmUtility)
                val cutWithAccount = OlmEventService(
                    "",
                    alice,
                    aliceDeviceId,
                    Ed25519Key(null, aliceAccount.identityKeys.ed25519),
                    Curve25519Key(null, aliceAccount.identityKeys.curve25519),
                    json,
                    aliceAccount,
                    store,
                    api,
                    aliceSignService
                )
                store.keys.updateDeviceKeys(bob) {
                    mapOf(
                        bobDeviceId to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    userId = bob,
                                    deviceId = bobDeviceId,
                                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                                    keys = Keys(
                                        keysOf(
                                            bobOlmService.getSelfSignedDeviceKeys().signed.get<Curve25519Key>()!!,
                                            bobOlmService.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!
                                        )
                                    )
                                ), mapOf()
                            ), KeySignatureTrustLevel.Valid(true)
                        )
                    )
                }
                bobStore.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDeviceId to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    userId = alice,
                                    deviceId = aliceDeviceId,
                                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                                    keys = Keys(
                                        keysOf(
                                            Curve25519Key(null, aliceAccount.identityKeys.curve25519),
                                            Ed25519Key(null, aliceAccount.identityKeys.ed25519)
                                        )
                                    )
                                ), mapOf()
                            ), KeySignatureTrustLevel.Valid(true)
                        )
                    )
                }

                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, ClaimKeys()) {
                        it.oneTimeKeys shouldBe mapOf(alice to mapOf(aliceDeviceId to KeyAlgorithm.SignedCurve25519))
                        ClaimKeys.Response(
                            emptyMap(),
                            mapOf(
                                alice to mapOf(
                                    aliceDeviceId to keysOf(
                                        aliceSignService.signCurve25519Key(
                                            Curve25519Key(
                                                aliceDeviceId,
                                                aliceAccount.getOneTimeKey()
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                }

                val outboundSession = OlmOutboundGroupSession.create()
                val eventContent = RoomKeyEventContent(
                    RoomId("room", "server"),
                    outboundSession.sessionId,
                    outboundSession.sessionKey,
                    EncryptionAlgorithm.Megolm
                )
                val encryptedEvent = Event.ToDeviceEvent(
                    bobOlmService.event.encryptOlm(
                        eventContent,
                        alice,
                        aliceDeviceId
                    ), bob
                )

                val emittedEvent = async { cutWithAccount.decryptedOlmEvents.first() }
                delay(50)
                cutWithAccount.handleOlmEncryptedToDeviceEvents(encryptedEvent)

                assertSoftly(
                    emittedEvent.await()
                ) {
                    assertNotNull(this)
                    encrypted shouldBe encryptedEvent
                    decrypted shouldBe DecryptedOlmEvent(
                        eventContent,
                        bob,
                        keysOf(bobOlmService.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!.copy(keyId = null)),
                        alice,
                        keysOf(Ed25519Key(null, aliceAccount.identityKeys.ed25519))
                    )
                }
            }
        }
    }
    context(OlmEventService::encryptOlm.name) {
        val eventContent = RoomKeyEventContent(
            RoomId("room", "server"),
            "sessionId",
            "sessionKey",
            EncryptionAlgorithm.Megolm,
        )
        lateinit var decryptedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>
        beforeEach {
            decryptedOlmEvent = DecryptedOlmEvent(
                content = eventContent,
                sender = alice,
                senderKeys = keysOf(aliceEdKey.copy(keyId = null)),
                recipient = bob,
                recipientKeys = keysOf(bobEdKey.copy(keyId = null))
            )
        }
        context("without stored olm encrypt session") {
            beforeEach {
                apiConfig.endpoints { claimKeysEndpoint() }
            }
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
                        decryptedOlmEventSerializer,
                        bobSession.decrypt(OlmMessage(encryptedCipherText.body, OlmMessageType.INITIAL_PRE_KEY))
                    ) shouldBe decryptedOlmEvent
                }
                store.olm.getOlmSessions(bobCurveKey)!! shouldHaveSize 1
            }
            should("throw exception when one time key is invalid") {
                signService.returnVerify = VerifyResult.Invalid("dino")

                shouldThrow<KeyException.KeyVerificationFailedException> {
                    cut.encryptOlm(eventContent, bob, bobDeviceId).ciphertext.entries.first().value
                }.message shouldBe "dino"
                store.olm.getOlmSessions(bobCurveKey) should beNull()
            }
        }
        context("with stored olm encrypt session") {
            should("encrypt event with stored session") {
                freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.getOneTimeKey()
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
                        decryptedOlmEventSerializer,
                        bobSession.decrypt(
                            OlmMessage(
                                encryptedCipherText.body,
                                OlmMessageType.INITIAL_PRE_KEY
                            )
                        )
                    ) shouldBe decryptedOlmEvent
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
        lateinit var decryptedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>
        beforeEach {
            decryptedOlmEvent = DecryptedOlmEvent(
                content = eventContent,
                sender = bob,
                senderKeys = keysOf(bobEdKey),
                recipient = alice,
                recipientKeys = keysOf(aliceEdKey)
            )
        }
        context("without stored decrypt olm session") {
            should("decrypt pre key message from new session") {
                val encryptedMessage = freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.getOneTimeKey()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
                }
                cut.decryptOlm(
                    OlmEncryptedEventContent(
                        ciphertext = mapOf(
                            aliceCurveKey.value to CiphertextInfo(encryptedMessage.cipherText, INITIAL_PRE_KEY)
                        ),
                        senderKey = bobCurveKey
                    ), bob
                ) shouldBe decryptedOlmEvent
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
                        aliceAccount.getOneTimeKey()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
                }
                repeat(5) { pseudoSessionId ->
                    freeAfter(OlmAccount.create()) { dummyAccount ->
                        freeAfter(
                            OlmSession.createOutbound(
                                aliceAccount,
                                dummyAccount.identityKeys.curve25519,
                                dummyAccount.getOneTimeKey()
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
                var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
                apiConfig.endpoints {
                    claimKeysEndpoint()
                    matrixJsonEndpoint(
                        json, mappings,
                        SendToDevice("m.room.encrypted", "txn"),
                        skipUrlCheck = true
                    ) {
                        sendToDeviceEvents = it.messages
                    }
                }
                val encryptedMessage = freeAfter(
                    OlmSession.createOutbound(
                        bobAccount,
                        aliceCurveKey.value,
                        aliceAccount.getOneTimeKey()
                    )
                ) { bobSession ->
                    bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
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

                val encryptedEventContent =
                    sendToDeviceEvents?.get(bob)?.get(bobDeviceId)?.shouldBeInstanceOf<OlmEncryptedEventContent>()
                val ciphertext = encryptedEventContent?.ciphertext?.get(bobCurveKey.value)?.body
                assertNotNull(ciphertext)
                freeAfter(OlmSession.createInbound(bobAccount, ciphertext)) { session ->
                    json.decodeFromString(
                        decryptedOlmEventSerializer,
                        session.decrypt(OlmMessage(ciphertext, OlmMessageType.INITIAL_PRE_KEY))
                    ).content shouldBe DummyEventContent
                }
            }
        }
        context("with stored decrypt olm session") {
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
                        bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
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
                    ) shouldBe decryptedOlmEvent
                    store.olm.getOlmSessions(bobCurveKey)?.first() shouldNotBe storedOlmSession
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
                        bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
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
                    ) shouldBe decryptedOlmEvent
                    store.olm.getOlmSessions(bobCurveKey)?.first() shouldNotBe storedOlmSession
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
                        bobSession.encrypt(json.encodeToString(decryptedOlmEventSerializer, decryptedOlmEvent))
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
                    ) shouldBe decryptedOlmEvent
                    store.olm.getOlmSessions(bobCurveKey)!! shouldNotContain storedOlmSession1
                }
            }
        }
        context("handle olm event with manipulated") {
            suspend fun ContainerScope.handleManipulation(manipulatedOlmEvent: DecryptedOlmEvent<RoomKeyEventContent>) {
                val job1 = launch {
                    store.keys.outdatedKeys.first { it.isNotEmpty() }
                    store.keys.outdatedKeys.value = setOf()
                }
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
            should("sender") {
                handleManipulation(decryptedOlmEvent.copy(sender = UserId("cedric", "server")))
            }
            should("senderKeys") {
                handleManipulation(decryptedOlmEvent.copy(senderKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key"))))
            }
            should("recipient") {
                handleManipulation(decryptedOlmEvent.copy(recipient = UserId("cedric", "server")))
            }
            should("recipientKeys") {
                handleManipulation(
                    decryptedOlmEvent.copy(recipientKeys = keysOf(Ed25519Key("CEDRICKEY", "cedrics key")))
                )
            }
        }
    }
    context(OlmEventService::encryptMegolm.name) {
        val eventContent = TextMessageEventContent("Hi", relatesTo = relatesTo)
        val room = RoomId("room", "server")
        val decryptedMegolmEvent = DecryptedMegolmEvent(eventContent, room)
        beforeEach {
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
            expectedMessageCount: Int,
        ) {
            should("encrypt message") {
                var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
                apiConfig.endpoints {
                    claimKeysEndpoint()
                    matrixJsonEndpoint(
                        json, mappings,
                        SendToDevice("m.room.encrypted", "txn"),
                        skipUrlCheck = true
                    ) {
                        sendToDeviceEvents = it.messages
                    }
                }
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

                    val ciphertext =
                        sendToDeviceEvents?.get(bob)?.get(bobDeviceId)?.shouldBeInstanceOf<OlmEncryptedEventContent>()
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
                            decryptedMegolmEventSerializer, inboundSession.decrypt(result.ciphertext).message
                        ) shouldBe decryptedMegolmEvent
                    }
                }
            }
        }
        context("without stored megolm session") {
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
            }
            should("wait that room members are loaded") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        SendToDevice("m.room.encrypted", "txn"),
                        skipUrlCheck = true
                    ) {
                    }
                }
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
        context("with stored megolm session") {
            context("send sessions to new devices and encrypt") {
                beforeEach {
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
                beforeEach {
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
                beforeEach {
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
        val decryptedMegolmEvent = DecryptedMegolmEvent(eventContent, room)
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
                val ciphertext =
                    session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
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
                ) shouldBe decryptedMegolmEvent.copy(content = decryptedMegolmEvent.content.copy(relatesTo = relatesTo))
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
                val ciphertext =
                    session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
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
                            decryptedMegolmEventSerializer,
                            decryptedMegolmEvent.copy(roomId = RoomId("other", "server"))
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
                    val ciphertext =
                        session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
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
                    val ciphertext =
                        session.encrypt(json.encodeToString(decryptedMegolmEventSerializer, decryptedMegolmEvent))
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