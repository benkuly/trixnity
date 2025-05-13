package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.client.verification.SelfVerificationMethod.*
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods.PreconditionsNotMet.Reason
import net.folivo.trixnity.clientserverapi.client.SyncBatchTokenStore
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationServiceTest : TrixnityBaseTest() {

    init {
        testScope.testScheduler.advanceTimeBy(10.days)
    }

    private val aliceUserId = UserId("alice", "server")
    private val aliceDeviceId = "AAAAAA"
    private val bobUserId = UserId("bob", "server")
    private val bobDeviceId = "BBBBBB"
    private val eventId = EventId("$1event")
    private val roomId = RoomId("room", "server")

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    private val olmDecrypterMock = OlmDecrypterMock()
    private val olmEncryptionServiceMock = OlmEncryptionServiceMock()
    private val syncBatchTokenStore = SyncBatchTokenStore.inMemory()
    private val roomServiceMock = RoomServiceMock()
    private val userServiceMock = UserServiceMock()
    private val keyServiceMock = KeyServiceMock()
    private val keyTrustServiceMock = KeyTrustServiceMock()
    private val keySecretServiceMock = KeySecretServiceMock()

    private val keyStore = getInMemoryKeyStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(
        config = apiConfig,
        syncBatchTokenStore = syncBatchTokenStore
    )
    private val cut = VerificationServiceImpl(
        userInfo = UserInfo(aliceUserId, aliceDeviceId, Key.Ed25519Key(null, ""), Curve25519Key(null, "")),
        api = api,
        keyStore = keyStore,
        globalAccountDataStore = globalAccountDataStore,
        olmDecrypter = olmDecrypterMock,
        olmEncryptionService = olmEncryptionServiceMock,
        roomService = roomServiceMock,
        keyService = keyServiceMock,
        userService = userServiceMock,
        keyTrustService = keyTrustServiceMock,
        keySecretService = keySecretServiceMock,
        currentSyncState = CurrentSyncState(currentSyncState),
        clock = testScope.testClock,
    ).apply {
        startInCoroutineScope(testScope.backgroundScope)
    }

    @Test
    fun `init » handleVerificationRequestEvents » ignore request that is timed out`() = runTest {
        val request = VerificationRequestToDeviceEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync()) {
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

    @Test
    fun `init » handleVerificationRequestEvents » add device verification`() = runTest {
        val request = VerificationRequestToDeviceEventContent(
            bobDeviceId,
            setOf(Sas),
            currentTime,
            "transaction1"
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync()) {
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

    @Test
    fun `init » handleVerificationRequestEvents » cancel second verification request`() = runTest {
        val request1 = VerificationRequestToDeviceEventContent(
            bobDeviceId,
            setOf(Sas),
            currentTime,
            "transaction1"
        )
        val request2 = VerificationRequestToDeviceEventContent(
            aliceDeviceId,
            setOf(Sas),
            currentTime,
            "transaction2"
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync()) {
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
            matrixJsonEndpoint(SendToDevice("m.key.verification.cancel", "*")) {
            }
        }
        api.sync.startOnce().getOrThrow()

        val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
        require(activeDeviceVerification != null)
        activeDeviceVerification.theirDeviceId shouldBe bobDeviceId

        delay(1.seconds)

        olmEncryptionServiceMock.encryptOlmCalled shouldBe Triple(
            VerificationCancelEventContent(Code.User, "user cancelled verification", null, "transaction2"),
            aliceUserId,
            aliceDeviceId
        )
    }

    @Test
    fun `init » handleOlmDecryptedDeviceVerificationRequestEvents » ignore request that is timed out`() = runTest {
        val request = VerificationRequestToDeviceEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")), bobUserId),
                DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
            )
        )
        cut.activeDeviceVerification.value shouldBe null
    }

    @Test
    fun `init » handleOlmDecryptedDeviceVerificationRequestEvents » add device verification`() = runTest {
        val request = VerificationRequestToDeviceEventContent(
            bobDeviceId,
            setOf(Sas),
            currentTime,
            "transaction1"
        )
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")), bobUserId),
                DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
            )
        )
        val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
        require(activeDeviceVerification != null)
        activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
    }

    @Test
    fun `init » handleOlmDecryptedDeviceVerificationRequestEvents » cancel second device verification`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(SendToDevice("m.key.verification.cancel", "*")) {
            }
        }

        val request1 = VerificationRequestToDeviceEventContent(
            bobDeviceId,
            setOf(Sas),
            currentTime,
            "transaction1"
        )
        val request2 = VerificationRequestToDeviceEventContent(
            aliceDeviceId,
            setOf(Sas),
            currentTime,
            "transaction2"
        )
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")), bobUserId),
                DecryptedOlmEvent(request1, bobUserId, keysOf(), aliceUserId, keysOf())
            )
        )
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")), bobUserId),
                DecryptedOlmEvent(request2, aliceUserId, keysOf(), aliceUserId, keysOf())
            )
        )
        val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
        require(activeDeviceVerification != null)
        activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
        eventually(1.seconds) {
            olmEncryptionServiceMock.encryptOlmCalled shouldBe Triple(
                VerificationCancelEventContent(
                    Code.User,
                    "already have an active device verification",
                    null,
                    "transaction2"
                ),
                aliceUserId,
                aliceDeviceId
            )
        }
    }

    @Test
    fun `init » startLifecycleOfActiveVerifications » start all lifecycles of device verifications`() = runTest {
        val request = VerificationRequestToDeviceEventContent(
            bobDeviceId,
            setOf(Sas),
            currentTime,
            "transaction"
        )
        syncBatchTokenStore.setSyncBatchToken("token1")
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(since = "token1")) {
                Sync.Response(
                    nextBatch = "nextBatch1",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                )
            }
            matrixJsonEndpoint(Sync(since = "nextBatch1")) {
                Sync.Response(
                    nextBatch = "nextBatch2",
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

    @Test
    fun `init » startLifecycleOfActiveVerifications » start all lifecycles of user verifications`() = runTest {
        val nextEventId = EventId("$1nextEventId")
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendMessageEvent(roomId, "m.room.message", "transaction1"),
            ) {
                SendEventResponse(EventId("$24event"))
            }
        }
        val timelineEvent = TimelineEvent(
            event = MessageEvent(
                VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                eventId,
                bobUserId,
                roomId,
                currentTime
            ),
            previousEventId = null,
            nextEventId = nextEventId,
            gap = null
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(
                            Code.User, "user",
                            transactionId = null,
                            relatesTo = RelatesTo.Reference(eventId)
                        ),
                        nextEventId,
                        bobUserId,
                        roomId,
                        currentTime
                    ),
                    previousEventId = null,
                    nextEventId = nextEventId,
                    gap = null
                )
            )
        )
        val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)?.state
        assertNotNull(result)
        result.first { it is Cancel } shouldBe Cancel(
            VerificationCancelEventContent(Code.User, "user", RelatesTo.Reference(eventId), null),
            false
        )
    }


    @Test
    fun `createDeviceVerificationRequest » send request to device and save locally`() = runTest {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.key.verification.request", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        olmEncryptionServiceMock.returnEncryptOlm =
            Result.failure(OlmEncryptionService.EncryptOlmError.OlmLibraryError(OlmLibraryException("dino")))
        val createdVerification = cut.createDeviceVerificationRequest(bobUserId, setOf(bobDeviceId)).getOrThrow()
        val activeDeviceVerification = cut.activeDeviceVerification.filterNotNull().first()
        createdVerification shouldBe activeDeviceVerification
        assertSoftly(sendToDeviceEvents) {
            this?.shouldHaveSize(1)
            this?.get(bobUserId)?.get(bobDeviceId)
                ?.shouldBeInstanceOf<VerificationRequestToDeviceEventContent>()?.fromDevice shouldBe aliceDeviceId
        }
    }

    @Test
    fun `createUserVerificationRequest » no direct room with user exists » create room and send request into it`() =
        runTest {
            var createRoomCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(CreateRoom()) {
                    createRoomCalled = true
                    it.invite shouldBe setOf(bobUserId)
                    it.isDirect shouldBe true
                    CreateRoom.Response(roomId)
                }
            }
            val result = async { cut.createUserVerificationRequest(bobUserId).getOrThrow() }
            val message = roomServiceMock.sentMessages.first { it.isNotEmpty() }.first().second
            roomServiceMock.outbox.value =
                listOf(
                    flowOf(
                        RoomOutboxMessage(
                            transactionId = "1",
                            roomId = roomId,
                            content = message,
                            eventId = EventId("bla"),
                            createdAt = Instant.fromEpochMilliseconds(currentTime),
                        )
                    )
                )

            result.await()
            createRoomCalled shouldBe true
        }

    @Test
    fun `createUserVerificationRequest » direct room with user exists » send request to existing room`() = runTest {
        globalAccountDataStore.save(
            GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
        )
        userServiceMock.roomUsers.put(
            Pair(bobUserId, roomId), flowOf(
                RoomUser(
                    roomId, bobUserId, "Bob",
                    ClientEvent.RoomEvent.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        id = EventId("0"),
                        sender = bobUserId,
                        roomId = roomId,
                        Clock.System.now().toEpochMilliseconds(),
                        stateKey = bobUserId.full
                    )
                )
            )
        )
        val result = async { cut.createUserVerificationRequest(bobUserId).getOrThrow() }
        val message = roomServiceMock.sentMessages.first { it.isNotEmpty() }.first().second
        roomServiceMock.outbox.value =
            listOf(
                flowOf(
                    RoomOutboxMessage(
                        transactionId = "1",
                        roomId = roomId,
                        content = message,
                        eventId = EventId("bla"),
                        createdAt = Instant.fromEpochMilliseconds(currentTime),
                    )
                )
            )
        result.await()
    }

    @Test
    fun `createUserVerificationRequest » direct room with user exists but other user left and a new direct room was created » send request to room with other user present`() =
        runTest {
            val abandonedRoom = RoomId("abandonedRoom", "Server")
            globalAccountDataStore.save(
                GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(abandonedRoom, roomId))))
            )
            userServiceMock.roomUsers.put(
                Pair(bobUserId, abandonedRoom), flowOf(
                    RoomUser(
                        abandonedRoom, bobUserId, "Bob",
                        ClientEvent.RoomEvent.StateEvent(
                            MemberEventContent(membership = Membership.LEAVE),
                            id = EventId("0"),
                            sender = bobUserId,
                            roomId = abandonedRoom,
                            Clock.System.now().toEpochMilliseconds(),
                            stateKey = bobUserId.full
                        )
                    )
                )
            )
            userServiceMock.roomUsers.put(
                Pair(bobUserId, roomId), flowOf(
                    RoomUser(
                        roomId, bobUserId, "Bob",
                        ClientEvent.RoomEvent.StateEvent(
                            MemberEventContent(membership = Membership.JOIN),
                            id = EventId("0"),
                            sender = bobUserId,
                            roomId = roomId,
                            Clock.System.now().toEpochMilliseconds(),
                            stateKey = bobUserId.full
                        )
                    )
                )
            )
            val result = async { cut.createUserVerificationRequest(bobUserId).getOrThrow() }
            val message = roomServiceMock.sentMessages.first { it.isNotEmpty() }.first().second
            roomServiceMock.outbox.value =
                listOf(
                    flowOf(
                        RoomOutboxMessage(
                            transactionId = "1",
                            roomId = roomId,
                            content = message,
                            eventId = EventId("bla"),
                            createdAt = Clock.System.now(),
                        )
                    )
                )
            result.await()
        }

    @Test
    fun `return PreconditionsNotMet when initial sync is still running`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            currentSyncState.value = SyncState.INITIAL_SYNC
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.SyncNotRunning))
        }

    @Test
    fun `return PreconditionsNotMet when device keys not fetched yet`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.DeviceKeysNotFetchedYet))
        }

    @Test
    fun `return PreconditionsNotMet when cross signing keys not fetched yet`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.CrossSigningKeysNotFetchedYet))
        }

    @Test
    fun `return NoCrossSigningEnabled when cross signing keys are fetched but empty`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.NoCrossSigningEnabled
        }

    @Test
    fun `return AlreadyCrossSigned when already cross signed`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            cut.getSelfVerificationMethods().first() shouldBe SelfVerificationMethods.AlreadyCrossSigned
        }

    @Test
    fun `add CrossSignedDeviceVerification`() = runTest(setup = { getSelfVerificationMethodsSetup() }) {
        apiConfig.endpoints {
            matrixJsonEndpoint(SendToDevice("m.key.verification.request", "*")) {
            }
        }
        keyStore.updateCrossSigningKeys(aliceUserId) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateDeviceKeys(aliceUserId) {
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
        val result = cut.getSelfVerificationMethods().first()
            .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods
        result.size shouldBe 1
        val firstResult = result.first()
        firstResult.shouldBeInstanceOf<CrossSignedDeviceVerification>()
        firstResult.createDeviceVerification().getOrThrow().shouldBeInstanceOf<ActiveDeviceVerification>()
    }

    @Test
    fun `don't add CrossSignedDeviceVerification when there are no cross signed devices`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>()
                .methods.size shouldBe 0
        }

    @Test
    fun `don't add CrossSignedDeviceVerification when there is only a dehydrated device`() =
        runTest(setup = { getSelfVerificationMethodsSetup() }) {
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    ),
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    ),
                    "DEV2" to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, "DEV2", setOf(), keysOf(), true), null),
                        KeySignatureTrustLevel.CrossSigned(false)
                    ),
                )
            }
            cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>()
                .methods.size shouldBe 0
        }

    @Test
    fun `add AesHmacSha2RecoveryKeyWithPbkdf2Passphrase`() = runTest(setup = { getSelfVerificationMethodsSetup() }) {
        val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
            name = "default key",
            passphrase = null,
        )
        globalAccountDataStore.save(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
        globalAccountDataStore.save(GlobalAccountDataEvent(defaultKey, "KEY"))
        keyStore.updateCrossSigningKeys(aliceUserId) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateDeviceKeys(aliceUserId) {
            mapOf(
                aliceDeviceId to StoredDeviceKeys(
                    Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.NotCrossSigned
                )
            )
        }
        cut.getSelfVerificationMethods().first()
            .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
            AesHmacSha2RecoveryKey(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey)
        )
    }

    @Test
    fun `add AesHmacSha2RecoveryKey`() = runTest(setup = { getSelfVerificationMethodsSetup() }) {
        val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
            name = "default key",
            passphrase = SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2("salt", 10_000),
        )
        globalAccountDataStore.save(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
        globalAccountDataStore.save(GlobalAccountDataEvent(defaultKey, "KEY"))
        keyStore.updateCrossSigningKeys(aliceUserId) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateDeviceKeys(aliceUserId) {
            mapOf(
                aliceDeviceId to StoredDeviceKeys(
                    Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.NotCrossSigned
                )
            )
        }
        cut.getSelfVerificationMethods().first()
            .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
            AesHmacSha2RecoveryKey(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey),
            AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey)
        )
    }

    // TODO enable this on Js
    @Test
    fun `honor data class equality`() = runTest(setup = { getSelfVerificationMethodsSetup() }) {
        globalAccountDataStore.save(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                SecretKeyEventContent.AesHmacSha2Key(
                    name = "default key",
                    passphrase = SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2("salt", 10_000),
                ), "KEY"
            )
        )

        keyStore.updateCrossSigningKeys(aliceUserId) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateDeviceKeys(aliceUserId) {
            mapOf(
                aliceDeviceId to StoredDeviceKeys(
                    Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                    KeySignatureTrustLevel.NotCrossSigned
                ),
                "DEV2" to StoredDeviceKeys(
                    Signed(DeviceKeys(aliceUserId, "DEV2", setOf(), keysOf()), null),
                    KeySignatureTrustLevel.CrossSigned(false)
                ),
            )
        }

        val methods1 = cut.getSelfVerificationMethods().first()
            .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods
        val methods2 = cut.getSelfVerificationMethods().first()
            .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods

        methods1.size shouldBe 3
        methods1 shouldBe methods2
    }

    @Test
    fun `getActiveUserVerification » skip timed out verifications`() = runTest {
        val timelineEvent = TimelineEvent(
            event = MessageEvent(
                VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                eventId,
                bobUserId,
                roomId,
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
        val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
        result shouldBe null
    }

    @Test
    fun `getActiveUserVerification » return cached verification`() = runTest {
        val timelineEvent = TimelineEvent(
            event = MessageEvent(
                VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                eventId,
                bobUserId,
                roomId,
                currentTime,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
        val result1 = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
        assertNotNull(result1)
        val result2 = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
        result2 shouldBe result1
    }

    @Test
    fun `getActiveUserVerification » create verification from event`() = runTest {
        val timelineEvent = TimelineEvent(
            event = MessageEvent(
                VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                eventId,
                bobUserId,
                roomId,
                currentTime,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
        val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
        val state = result?.state
        assertNotNull(state)
        state.value.shouldBeInstanceOf<TheirRequest>()
    }

    @Test
    fun `getActiveUserVerification » not create verification from own request event`() = runTest {
        val timelineEvent = TimelineEvent(
            event = MessageEvent(
                VerificationRequest(aliceDeviceId, bobUserId, setOf(Sas)),
                eventId,
                aliceUserId,
                roomId,
                currentTime,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
        cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId) shouldBe null
    }

    private fun getSelfVerificationMethodsSetup() {
        clearOutdatedKeys { keyStore }
        currentSyncState.value = SyncState.RUNNING
    }
}