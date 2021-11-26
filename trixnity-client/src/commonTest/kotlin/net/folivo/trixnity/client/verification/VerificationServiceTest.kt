package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.testutils.createInMemoryStore
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Request
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.RequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStepRelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

class VerificationServiceTest : ShouldSpec({
    timeout = 30_000
    val aliceUserId = UserId("alice", "server")
    val aliceDeviceId = "AAAAAA"
    val bobUserId = UserId("bob", "server")
    val bobDeviceId = "BBBBBB"
    val eventId = EventId("$1event")
    val roomId = RoomId("room", "server")
    val api = mockk<MatrixApiClient>()
    lateinit var storeScope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val room = mockk<RoomService>()
    val user = mockk<UserService>(relaxUnitFun = true)
    val json = createMatrixJson()
    lateinit var requestEventFlow: MutableSharedFlow<Event<RequestEventContent>>
    lateinit var stepEventFlow: MutableSharedFlow<Event<VerificationStep>>
    lateinit var decryptedOlmEventFlow: MutableSharedFlow<OlmService.DecryptedOlmEvent>
    lateinit var cut: VerificationService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = createInMemoryStore(storeScope)
        requestEventFlow = MutableSharedFlow(replay = 1)
        stepEventFlow = MutableSharedFlow(replay = 1)
        decryptedOlmEventFlow = MutableSharedFlow()
        coEvery { api.sync.events<RequestEventContent>() } returns requestEventFlow
        coEvery { api.sync.events<VerificationStep>() } returns stepEventFlow
        coEvery { olm.decryptedOlmEvents } returns decryptedOlmEventFlow
        coEvery { api.json } returns json
        cut = VerificationService(
            ownUserId = aliceUserId,
            ownDeviceId = aliceDeviceId,
            api = api,
            store = store,
            olm = olm,
            room = room,
            user = user,
            loggerFactory = LoggerFactory.default
        )
    }
    afterTest {
        storeScope.cancel()
        clearAllMocks()
    }
    context(VerificationService::startEventHandling.name) {
        lateinit var eventHandlingCoroutineScope: CoroutineScope
        beforeTest {
            eventHandlingCoroutineScope = CoroutineScope(Dispatchers.Default)
            cut.startEventHandling(eventHandlingCoroutineScope)
        }
        afterTest {
            eventHandlingCoroutineScope.cancel()
        }
        context("handleVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = RequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                requestEventFlow.emit(ToDeviceEvent(request, bobUserId))
                val activeDeviceVerifications = cut.activeDeviceVerifications.value
                activeDeviceVerifications shouldHaveSize 0
            }
            should("add device verification") {
                val request = RequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                requestEventFlow.emit(ToDeviceEvent(request, bobUserId))
                val activeDeviceVerifications = cut.activeDeviceVerifications.first { it.isNotEmpty() }
                activeDeviceVerifications shouldHaveSize 1
                activeDeviceVerifications[0].state.value.shouldBeInstanceOf<Request>()
            }
        }
        context("handleOlmDecryptedDeviceVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = RequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request, bobUserId, mockk(), mockk(), mockk())
                    )
                )
                val activeDeviceVerifications = cut.activeDeviceVerifications.value
                activeDeviceVerifications shouldHaveSize 0
            }
            should("add device verification") {
                val request = RequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request, bobUserId, mockk(), mockk(), mockk())
                    )
                )
                val activeDeviceVerifications = cut.activeDeviceVerifications.first { it.isNotEmpty() }
                activeDeviceVerifications shouldHaveSize 1
                activeDeviceVerifications[0].state.value.shouldBeInstanceOf<Request>()
            }
        }
        context("startLifecycleOfActiveVerifications") {
            should("start all lifecycles of device verifications") {
                val request = RequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction"
                )
                requestEventFlow.emit(ToDeviceEvent(request, bobUserId))
                stepEventFlow.emit(
                    ToDeviceEvent(
                        CancelEventContent(Code.User, "user", null, "transaction"),
                        bobUserId
                    )
                )
                val activeDeviceVerifications = cut.activeDeviceVerifications.first { it.isNotEmpty() }
                activeDeviceVerifications[0].state.first { it is Cancel } shouldBe Cancel(
                    CancelEventContent(Code.User, "user", null, "transaction"),
                    bobUserId
                )
                cut.activeDeviceVerifications.first { it.isEmpty() } shouldHaveSize 0
            }
            should("start all lifecycles of user verifications") {
                val nextEventId = EventId("$1nextEventId")
                coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("$24event")
                val timelineEvent = TimelineEvent(
                    event = Event.MessageEvent(
                        VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
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
                coEvery { room.getTimelineEvent(eventId, roomId, any()) } returns MutableStateFlow(timelineEvent)
                store.roomTimeline.addAll(listOf(timelineEvent))
                coEvery { room.getNextTimelineEvent(any(), any()) } returns MutableStateFlow(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            CancelEventContent(
                                Code.User, "user",
                                transactionId = null,
                                relatesTo = VerificationStepRelatesTo(eventId)
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
                val result = cut.getActiveUserVerification(eventId, roomId)
                assertNotNull(result)
                result.state.first { it is Cancel } shouldBe Cancel(
                    CancelEventContent(Code.User, "user", VerificationStepRelatesTo(eventId), null),
                    bobUserId
                )
            }
        }
    }
    context(VerificationService::createDeviceVerificationRequest.name) {
        should("send request to device and save locally") {
            coEvery { api.users.sendToDevice<ToDeviceEventContent>(any(), any(), any()) } just Runs
            coEvery { olm.events.encryptOlm(any(), any(), any()) } throws OlmLibraryException("dino")
            cut.createDeviceVerificationRequest(bobUserId, bobDeviceId)
            val activeDeviceVerifications = cut.activeDeviceVerifications.first { it.isNotEmpty() }
            activeDeviceVerifications shouldHaveSize 1
            coVerify {
                api.users.sendToDevice<RequestEventContent>(withArg {
                    it shouldHaveSize 1
                    it[bobUserId]?.get(bobDeviceId)?.fromDevice shouldBe aliceDeviceId
                }, any(), any())
            }
        }
    }
    context(VerificationService::createUserVerificationRequest.name) {
        beforeTest {
            coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("$1event")
            store.room.update(roomId) {
                Room(roomId, encryptionAlgorithm = EncryptionAlgorithm.Megolm)
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
                coEvery { olm.events.encryptMegolm(any(), any(), any()) } throws OlmLibraryException("dino")
                coEvery { api.rooms.createRoom(invite = setOf(bobUserId), isDirect = true) } returns roomId
                cut.createUserVerificationRequest(bobUserId)
                coVerify {
                    api.rooms.sendMessageEvent(
                        roomId,
                        VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                        any()
                    )
                }
            }
        }
        context("direct room with user exists") {
            should("send request to existing room") {
                coEvery { olm.events.encryptMegolm(any(), any(), any()) } throws OlmLibraryException("dino")
                store.globalAccountData.update(
                    Event.GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
                )
                cut.createUserVerificationRequest(bobUserId)
                coVerify {
                    api.rooms.sendMessageEvent(
                        roomId,
                        VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                        any()
                    )
                }
            }
        }
    }
    context(VerificationService::getActiveUserVerification.name) {
        should("skip timed out verifications") {
            store.roomTimeline.addAll(
                listOf(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
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
                )
            )
            val result = cut.getActiveUserVerification(eventId, roomId)
            result shouldBe null
        }
        should("return cached verification") {
            store.roomTimeline.addAll(
                listOf(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
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
                )
            )
            val result1 = cut.getActiveUserVerification(eventId, roomId)
            assertNotNull(result1)
            val result2 = cut.getActiveUserVerification(eventId, roomId)
            result2 shouldBe result1
        }
        should("create verification from event") {
            store.roomTimeline.addAll(
                listOf(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
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
                )
            )
            val result = cut.getActiveUserVerification(eventId, roomId)
            val state = result?.state
            assertNotNull(state)
            state.value.shouldBeInstanceOf<Request>()
        }
    }
})