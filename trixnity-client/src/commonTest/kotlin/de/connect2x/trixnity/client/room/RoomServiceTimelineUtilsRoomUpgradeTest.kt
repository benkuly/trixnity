package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryRoomAccountDataStore
import de.connect2x.trixnity.client.getInMemoryRoomOutboxMessageStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.getInMemoryRoomStore
import de.connect2x.trixnity.client.getInMemoryRoomTimelineStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.MediaServiceMock
import de.connect2x.trixnity.client.mocks.RoomEventEncryptionServiceMock
import de.connect2x.trixnity.client.mocks.TimelineEventHandlerMock
import de.connect2x.trixnity.client.simpleRoom
import de.connect2x.trixnity.client.simpleUserInfo
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.clientserverapi.client.SyncBatchTokenStore
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.BACKWARDS
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.FORWARDS
import de.connect2x.trixnity.clientserverapi.model.room.JoinRoom
import de.connect2x.trixnity.clientserverapi.model.room.JoinRoomVia
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class RoomServiceTimelineUtilsRoomUpgradeTest : TrixnityBaseTest() {
    private val room = simpleRoom.roomId
    private val newRoom = RoomId("!new:server")

    private val sender = UserId("sender", "server")

    private val roomStore = getInMemoryRoomStore()
    private val roomStateStore = getInMemoryRoomStateStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()

    private val mediaServiceMock = MediaServiceMock()
    private val roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock(true)
    private val timelineEventHandlerMock = TimelineEventHandlerMock()
    private val currentSyncState = MutableStateFlow(SyncState.RUNNING)
    private val syncBatchTokenStore = SyncBatchTokenStore.inMemory()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(
        config = apiConfig,
        syncBatchTokenStore = syncBatchTokenStore,
    )

    private val cut =
        RoomServiceImpl(
            api = api,
            roomStore = roomStore,
            roomStateStore = roomStateStore,
            roomAccountDataStore = getInMemoryRoomAccountDataStore(),
            roomTimelineStore = roomTimelineStore,
            roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(),
            roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
            mediaService = mediaServiceMock,
            forgetRoomService = { _, _ -> },
            userInfo = simpleUserInfo,
            timelineEventHandler = timelineEventHandlerMock,
            clock = testScope.testClock,
            matrixClientConfig = MatrixClientConfiguration(autoJoinUpgradedRooms = true),
            typingEventHandler = TypingEventHandlerImpl(api),
            currentSyncState = CurrentSyncState(currentSyncState),
            scope = testScope.backgroundScope
        )

    private fun encryptedEvent(i: Long = 24): MessageEvent<MegolmEncryptedMessageEventContent> {
        return MessageEvent(
            MegolmEncryptedMessageEventContent(
                ciphertext = MegolmMessageValue("cipher $i"),
                deviceId = "deviceId",
                sessionId = "senderId",
                senderKey = KeyValue.Curve25519KeyValue("key")
            ),
            EventId("\$event$i"),
            sender,
            room,
            i
        )
    }

    private val event0 = encryptedEvent(0)
    private val event1 = encryptedEvent(1)
    private val event2 = encryptedEvent(2)
    private val event3 = encryptedEvent(3)
    private val event4 = encryptedEvent(4)

    private val timelineEvent0 = TimelineEvent(
        event = event0,
        previousEventId = event1.id,
        nextEventId = event2.id,
        gap = null
    )
    private val timelineEvent1 = TimelineEvent(
        event = event1,
        previousEventId = null,
        nextEventId = event2.id,
        gap = TimelineEvent.Gap.GapBefore("1")
    )
    private val timelineEvent2 = TimelineEvent(
        event = event2,
        previousEventId = event1.id,
        nextEventId = event3.id,
        gap = null
    )
    private val timelineEvent3 = TimelineEvent(
        event = event3,
        previousEventId = event2.id,
        nextEventId = null,
        gap = TimelineEvent.Gap.GapAfter("3")
    )

    private val tombstoneEvent = StateEvent(
        TombstoneEventContent("upgrade", newRoom),
        EventId("\$tombstone"),
        sender,
        room,
        2000,
        stateKey = "",
    )
    private val createEvent1 = StateEvent(
        CreateEventContent(),
        EventId("\$event1"),
        sender,
        room,
        2000,
        stateKey = "",
    )
    private val createEvent2 = StateEvent(
        CreateEventContent(predecessor = CreateEventContent.PreviousRoom(room, EventId("\$tombstone"))),
        EventId("\$create"),
        sender,
        newRoom,
        2000,
        stateKey = "",
    )
    private val tombstoneTimelineEvent = TimelineEvent(
        event = tombstoneEvent,
        previousEventId = event2.id,
        nextEventId = null,
        gap = TimelineEvent.Gap.GapAfter("3")
    )
    private val createTimelineEvent1 = TimelineEvent(
        event = createEvent1,
        previousEventId = null,
        nextEventId = event2.id,
        gap = null
    )
    private val createTimelineEvent2 = TimelineEvent(
        event = createEvent2,
        previousEventId = null,
        nextEventId = event3.id,
        gap = null
    )

    private val timeline = listOf(
        createTimelineEvent1,
        timelineEvent2.copy(nextEventId = tombstoneTimelineEvent.eventId),
        tombstoneTimelineEvent,
        // new room
        createTimelineEvent2,
        timelineEvent3.copy(
            event = event3.copy(roomId = newRoom),
            previousEventId = createTimelineEvent2.eventId
        ),
    )

    private val newTimelineEvent1 = timelineEvent1.copy(gap = null)
    private val newTimelineEvent3 = TimelineEvent(
        event = event3,
        previousEventId = event2.id,
        nextEventId = event4.id,
        gap = null
    )
    private val timelineEvent4 = TimelineEvent(
        event = event4,
        previousEventId = event3.id,
        nextEventId = null,
        gap = null
    )

    private val joinCalled = MutableStateFlow<List<RoomId>>(emptyList())
    private val joinViaCalled = MutableStateFlow<List<RoomId>>(emptyList())
    private var exception: Throwable? = null

    init {
        apiConfig.apply {
            endpoints {
                matrixJsonEndpoint(JoinRoom(newRoom)) {
                    joinCalled.update { it + newRoom }
                    exception?.let {
                        exception = null
                        throw it
                    }
                    JoinRoom.Response(newRoom)
                }
                matrixJsonEndpoint(JoinRoom(room)) {
                    joinCalled.update { it + room }
                    exception?.let {
                        exception = null
                        throw it
                    }
                    JoinRoom.Response(room)
                }
                matrixJsonEndpoint(JoinRoomVia(newRoom.full, setOf("server"))) {
                    joinViaCalled.update { it + newRoom }
                    exception?.let {
                        exception = null
                        throw it
                    }
                    JoinRoomVia.Response(newRoom)
                }
                matrixJsonEndpoint(JoinRoomVia(room.full, setOf("server"))) {
                    joinViaCalled.update { it + room }
                    exception?.let {
                        exception = null
                        throw it
                    }
                    JoinRoomVia.Response(room)
                }
            }
        }
    }

    @BeforeTest
    fun setup() {
        joinCalled.value = emptyList()
        joinViaCalled.value = emptyList()
        exception = null
    }

    private fun testJoinBefore(roomKnown: Boolean) = runTest {
        roomUpgradeBeforeSetup(roomKnown)
        val result = backgroundScope.async {
            cut.getTimelineEvents(newRoom, event3.id, BACKWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        result.isActive shouldBe true
        roomStateStore.save(tombstoneEvent)
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(room) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(room) else listOf()
        result.await() shouldBe timeline.reversed()
    }

    private fun testJoinAfter(roomKnown: Boolean) = runTest {
        roomUpgradeAfterSetup(roomKnown)
        val result = backgroundScope.async {
            cut.getTimelineEvents(room, createEvent1.id, FORWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        result.isActive shouldBe true
        roomStateStore.save(createEvent2)
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(newRoom) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(newRoom) else listOf()
        result.await() shouldBe timeline
    }


    @Test
    fun `getTimelineEvents - room upgrade before - join room`() = testJoinBefore(true)

    @Test
    fun `getTimelineEvents - room upgrade before - join room via`() = testJoinBefore(false)

    @Test
    fun `getTimelineEvents - room upgrade after - join room`() = testJoinAfter(true)

    @Test
    fun `getTimelineEvents - room upgrade after - join room via`() = testJoinAfter(false)

    private fun testRetryBefore(roomKnown: Boolean) = runTest {
        roomUpgradeBeforeSetup(roomKnown)
        exception = MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown("error"))
        val result = backgroundScope.async {
            cut.getTimelineEvents(newRoom, event3.id, BACKWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        result.isActive shouldBe true
        roomStateStore.save(tombstoneEvent)
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(room, room) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(room, room) else listOf()
        result.await() shouldBe timeline.reversed()
    }

    private fun testRetryAfter(roomKnown: Boolean) = runTest {
        roomUpgradeAfterSetup(roomKnown)
        exception = MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown("error"))
        val result = backgroundScope.async {
            cut.getTimelineEvents(room, createEvent1.id, FORWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        result.isActive shouldBe true
        roomStateStore.save(createEvent2)
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(newRoom, newRoom) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(newRoom, newRoom) else listOf()
        result.await() shouldBe timeline
    }


    @Test
    fun `getTimelineEvents - room upgrade before - join room - exception - retry`() = testRetryBefore(true)

    @Test
    fun `getTimelineEvents - room upgrade before - join room via - exception - retry`() = testRetryBefore(false)

    @Test
    fun `getTimelineEvents - room upgrade after - join room - exception - retry`() = testRetryAfter(true)

    @Test
    fun `getTimelineEvents - room upgrade after - join room via - exception - retry`() = testRetryAfter(false)

    private fun testNoRetryBefore(roomKnown: Boolean) = runTest {
        roomUpgradeBeforeSetup(roomKnown)
        exception = MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.Forbidden("forbidden"))
        val result = backgroundScope.async {
            cut.getTimelineEvents(newRoom, event3.id, BACKWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(room) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(room) else listOf()
        result.await() shouldBe timeline.drop(3).reversed()
    }

    private fun testNoRetryAfter(roomKnown: Boolean) = runTest {
        roomUpgradeAfterSetup(roomKnown)
        exception = MatrixServerException(HttpStatusCode.Forbidden, ErrorResponse.Forbidden("forbidden"))
        val result = backgroundScope.async {
            cut.getTimelineEvents(room, createEvent1.id, FORWARDS) { minSize = 5 }
                .take(5).toList().map { it.first() }
        }
        delay(1.seconds)
        joinCalled.value shouldBe if (roomKnown) listOf(newRoom) else listOf()
        joinViaCalled.value shouldBe if (!roomKnown) listOf(newRoom) else listOf()
        result.await() shouldBe timeline.take(3)
    }

    @Test
    fun `getTimelineEvents - room upgrade before - join room - MatrixServerException 4xx - no retry`() =
        testNoRetryBefore(true)

    @Test
    fun `getTimelineEvents - room upgrade before - join room via - MatrixServerException 4xx - no retry`() =
        testNoRetryBefore(false)

    @Test
    fun `getTimelineEvents - room upgrade after - join room - MatrixServerException 4xx - no retry`() =
        testNoRetryAfter(true)

    @Test
    fun `getTimelineEvents - room upgrade after - join room via - MatrixServerException 4xx - no retry`() =
        testNoRetryAfter(false)

    private suspend fun roomUpgradeAfterSetup(roomKnown: Boolean) {
        roomStore.update(newRoom) {
            if (roomKnown) Room(newRoom, membership = Membership.INVITE) else null
        }
        roomTimelineStore.addAll(timeline)
        with(roomStateStore) {
            save(tombstoneEvent)
        }
    }

    private suspend fun roomUpgradeBeforeSetup(roomKnown: Boolean) {
        roomStore.update(room) {
            if (roomKnown) Room(room, membership = Membership.INVITE) else null
        }
        roomTimelineStore.addAll(timeline)
        with(roomStateStore) {
            save(createEvent2)
        }
    }
}
