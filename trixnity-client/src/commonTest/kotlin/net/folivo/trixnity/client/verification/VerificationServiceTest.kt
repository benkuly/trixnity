package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.client.verification.IVerificationService.SelfVerificationMethods
import net.folivo.trixnity.client.verification.SelfVerificationMethod.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class VerificationServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {

    timeout = 15_000
    val aliceUserId = UserId("alice", "server")
    val aliceDeviceId = "AAAAAA"
    val bobUserId = UserId("bob", "server")
    val bobDeviceId = "BBBBBB"
    val eventId = EventId("$1event")
    val roomId = RoomId("room", "server")
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var api: MatrixClientServerApiClient
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    lateinit var olmEventService: OlmEventServiceMock
    val keyService = KeyServiceMock()
    lateinit var room: RoomServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    lateinit var decryptedOlmEventFlow: MutableSharedFlow<IOlmService.DecryptedOlmEventContainer>

    lateinit var cut: VerificationService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope)
        decryptedOlmEventFlow = MutableSharedFlow()
        room = RoomServiceMock()
        olmEventService = OlmEventServiceMock(decryptedOlmEventFlow)
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        api = newApi
        cut = VerificationService(
            ownUserId = aliceUserId,
            ownDeviceId = aliceDeviceId,
            api = api,
            store = store,
            olmEventService = olmEventService,
            roomService = room,
            userService = UserServiceMock(),
            keyService = keyService,
            currentSyncState = currentSyncState,
            scope = scope,
        )
    }
    afterTest {
        scope.cancel()
    }

    context("init") {
        context("handleVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce().getOrThrow()

                val activeDeviceVerifications = cut.activeDeviceVerification.value
                activeDeviceVerifications shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce().getOrThrow()
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second verification request") {
                val request1 = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(
                                listOf(
                                    ToDeviceEvent(request1, bobUserId),
                                    ToDeviceEvent(request2, aliceUserId)
                                )
                            )
                        )
                    }
                    matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                    }
                }
                api.sync.startOnce().getOrThrow()

                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                eventually(1.seconds) {
                    olmEventService.encryptOlmCalled shouldBe Triple(
                        VerificationCancelEventContent(Code.User, "user cancelled verification", null, "transaction2"),
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("handleOlmDecryptedDeviceVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                decryptedOlmEventFlow.emit(
                    IOlmService.DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                cut.activeDeviceVerification.value shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                decryptedOlmEventFlow.emit(
                    IOlmService.DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second device verification") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                    }
                }

                val request1 = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                decryptedOlmEventFlow.emit(
                    IOlmService.DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request1, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                decryptedOlmEventFlow.emit(
                    IOlmService.DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request2, aliceUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                eventually(1.seconds) {
                    olmEventService.encryptOlmCalled shouldBe Triple(
                        VerificationCancelEventContent(Code.User, "user cancelled verification", null, "transaction2"),
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("startLifecycleOfActiveVerifications") {
            should("start all lifecycles of device verifications") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(
                                listOf(
                                    ToDeviceEvent(
                                        VerificationCancelEventContent(Code.User, "user", null, "transaction"),
                                        bobUserId
                                    )
                                )
                            )
                        )
                    }
                }
                api.sync.startOnce().getOrThrow()
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                api.sync.startOnce().getOrThrow()
                activeDeviceVerification.state.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", null, "transaction"),
                    false
                )
                cut.activeDeviceVerification.first { it == null } shouldBe null
            }
            should("start all lifecycles of user verifications") {
                val nextEventId = EventId("$1nextEventId")
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                    ) {
                        SendEventResponse(EventId("$24event"))
                    }
                }
                val timelineEvent = TimelineEvent(
                    event = Event.MessageEvent(
                        VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                        eventId,
                        bobUserId,
                        roomId,
                        Clock.System.now().toEpochMilliseconds()
                    ),
                    eventId = eventId,
                    roomId = roomId,
                    previousEventId = null,
                    nextEventId = nextEventId,
                    gap = null
                )
                room.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
                room.returnGetTimelineEvents = flowOf(
                    MutableStateFlow(
                        TimelineEvent(
                            event = Event.MessageEvent(
                                VerificationCancelEventContent(
                                    Code.User, "user",
                                    transactionId = null,
                                    relatesTo = RelatesTo.Reference(eventId)
                                ),
                                nextEventId,
                                bobUserId,
                                roomId,
                                Clock.System.now().toEpochMilliseconds()
                            ),
                            eventId = eventId,
                            roomId = roomId,
                            previousEventId = null,
                            nextEventId = nextEventId,
                            gap = null
                        )
                    )
                )
                val result = cut.getActiveUserVerification(timelineEvent)?.state
                assertNotNull(result)
                result.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", RelatesTo.Reference(eventId), null),
                    false
                )
            }
        }
    }
    context(VerificationService::createDeviceVerificationRequest.name) {
        should("send request to device and save locally") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.key.verification.request", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            olmEventService.returnEncryptOlm = { throw OlmLibraryException(message = "dino") }
            val createdVerification = cut.createDeviceVerificationRequest(bobUserId, bobDeviceId).getOrThrow()
            val activeDeviceVerification = cut.activeDeviceVerification.filterNotNull().first()
            createdVerification shouldBe activeDeviceVerification
            assertSoftly(sendToDeviceEvents) {
                this?.shouldHaveSize(1)
                this?.get(bobUserId)?.get(bobDeviceId)
                    ?.shouldBeInstanceOf<VerificationRequestEventContent>()?.fromDevice shouldBe aliceDeviceId
            }
        }
    }
    context(VerificationService::createUserVerificationRequest.name) {
        beforeTest {
            store.room.update(roomId) {
                Room(roomId, encryptionAlgorithm = EncryptionAlgorithm.Megolm, membersLoaded = true)
            }
            store.roomState.update(
                Event.StateEvent(
                    EncryptionEventContent(),
                    EventId("$24event"),
                    UserId("sender", "server"),
                    roomId,
                    1234,
                    stateKey = ""
                )
            )
        }
        context("no direct room with user exists") {
            should("create room and send request into it") {
                var sendMessageEventCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, CreateRoom()) {
                        it.invite shouldBe setOf(bobUserId)
                        it.isDirect shouldBe true
                        CreateRoom.Response(roomId)
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                        skipUrlCheck = true
                    ) {
                        it shouldBe VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas))
                        sendMessageEventCalled = true
                        SendEventResponse(EventId("$1event"))
                    }
                }
                olmEventService.returnEncryptMegolm = { throw OlmLibraryException(message = "dino") }
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                sendMessageEventCalled shouldBe true
            }
        }
        context("direct room with user exists") {
            should("send request to existing room") {
                var sendMessageEventCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                        skipUrlCheck = true
                    ) {
                        it shouldBe VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas))
                        sendMessageEventCalled = true
                        SendEventResponse(EventId("$1event"))
                    }
                }
                olmEventService.returnEncryptMegolm = { throw OlmLibraryException(message = "dino") }
                store.globalAccountData.update(
                    GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
                )
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                sendMessageEventCalled shouldBe true
            }
        }
    }
    context(VerificationService::getSelfVerificationMethods.name) {
        beforeTest {
            currentSyncState.value = SyncState.RUNNING
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet}, when device keys not fetched yet") {
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods(scope)
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet}, when cross signing keys not fetched yet") {
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            val result = cut.getSelfVerificationMethods(scope)
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet
        }
        should("return ${SelfVerificationMethods.NoCrossSigningEnabled}, when cross signing keys are fetched, but empty") {
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods(scope)
            result.first() shouldBe SelfVerificationMethods.NoCrossSigningEnabled
        }
        should("return ${SelfVerificationMethods.AlreadyCrossSigned} when already cross signed") {
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            cut.getSelfVerificationMethods(scope).first() shouldBe SelfVerificationMethods.AlreadyCrossSigned
        }
        should("add ${CrossSignedDeviceVerification::class.simpleName}") {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                }
            }
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    ),
                    "DEV2" to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, "DEV2", setOf(), keysOf()), null),
                        KeySignatureTrustLevel.CrossSigned(false)
                    ),
                    "DEV3" to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, "DEV3", setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(false)
                    )
                )
            }
            val result = cut.getSelfVerificationMethods(scope).first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods
            result.size shouldBe 1
            val firstResult = result.first()
            firstResult.shouldBeInstanceOf<CrossSignedDeviceVerification>()
            firstResult.createDeviceVerification().getOrThrow().shouldBeInstanceOf<ActiveDeviceVerification>()
        }
        should("don't add ${CrossSignedDeviceVerification::class.simpleName} when there are no cross signed devices") {
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods(scope).first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>()
                .methods.size shouldBe 0
        }
        should("add ${AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = null,
            )
            store.globalAccountData.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            store.globalAccountData.update(GlobalAccountDataEvent(defaultKey, "KEY"))
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods(scope).first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
                AesHmacSha2RecoveryKey(keyService, "KEY", defaultKey)
            )
        }
        should("add ${AesHmacSha2RecoveryKey::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2("salt", 300_000),
            )
            store.globalAccountData.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            store.globalAccountData.update(GlobalAccountDataEvent(defaultKey, "KEY"))
            store.keys.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods(scope).first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
                AesHmacSha2RecoveryKey(keyService, "KEY", defaultKey),
                AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keyService, "KEY", defaultKey)
            )
        }
    }
    context(VerificationService::getActiveUserVerification.name) {
        should("skip timed out verifications") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    1234
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result = cut.getActiveUserVerification(timelineEvent)
            result shouldBe null
        }
        should("return cached verification") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result1 = cut.getActiveUserVerification(timelineEvent)
            assertNotNull(result1)
            val result2 = cut.getActiveUserVerification(timelineEvent)
            result2 shouldBe result1
        }
        should("create verification from event") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result = cut.getActiveUserVerification(timelineEvent)
            val state = result?.state
            assertNotNull(state)
            state.value.shouldBeInstanceOf<TheirRequest>()
        }
        should("not create verification from own request event") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                    eventId,
                    aliceUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            cut.getActiveUserVerification(timelineEvent) shouldBe null
        }
    }
}