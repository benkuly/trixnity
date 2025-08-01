package net.folivo.trixnity.client.room

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.clientserverapi.client.SyncBatchTokenStore
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.core.model.keys.KeyValue
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RoomServiceTimelineUtilsTest : TrixnityBaseTest() {
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
            config = MatrixClientConfiguration(),
            typingEventHandler = TypingEventHandlerImpl(api),
            currentSyncState = CurrentSyncState(currentSyncState),
            scope = testScope.backgroundScope
        )

    private fun encryptedEvent(i: Long = 24): MessageEvent<MegolmEncryptedMessageEventContent> {
        return MessageEvent(
            MegolmEncryptedMessageEventContent(
                ciphertext = "cipher $i",
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

    private val createEvent = StateEvent(
        CreateEventContent(),
        EventId("\$event1"),
        sender,
        room,
        1,
        stateKey = ""
    )

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

    @Test
    fun `getTimelineEvents » all requested events in store » get timeline events backwards`() = runTest {
        allRequestedEventsInStoreSetup()
        cut.getTimelineEvents(room, event3.id)
            .take(3).toList().map { it.first() } shouldBe listOf(
            timelineEvent3,
            timelineEvent2,
            timelineEvent1
        )
    }

    @Test
    fun `getTimelineEvents » all requested events in store » get timeline events forwards`() = runTest {
        allRequestedEventsInStoreSetup()
        cut.getTimelineEvents(room, event1.id, FORWARDS)
            .take(3).toList().map { it.first() } shouldBe listOf(
            timelineEvent1,
            timelineEvent2,
            timelineEvent3
        )
    }

    @Test
    fun `getTimelineEvents » all requested events in store » get timeline events with maxSize`() = runTest {
        allRequestedEventsInStoreSetup()
        cut.getTimelineEvents(room, event1.id, FORWARDS) { maxSize = 2 }
            .toList().map { it.first() } shouldBe listOf(
            timelineEvent1,
            timelineEvent2,
        )
    }

    @Test
    fun `getTimelineEvents » not all events in store » fetch missing events by filling gaps`() = runTest {
        notAllRequestedEventsInStoreSetup()
        val result = async {
            cut.getTimelineEvents(room, event3.id).take(4).toList().map { it.first() }
        }
        timelineEventHandlerMock.unsafeFillTimelineGaps.first { it }
        roomTimelineStore.addAll(
            listOf(
                timelineEvent3,
                timelineEvent2.copy(gap = null, previousEventId = event0.id),
                timelineEvent0,
                timelineEvent1.copy(gap = null, nextEventId = event0.id)
            )
        )
        result.await() shouldBe listOf(
            timelineEvent3,
            timelineEvent2.copy(gap = null, previousEventId = event0.id),
            timelineEvent0,
            timelineEvent1.copy(gap = null, nextEventId = event0.id)
        )
    }

    @Test
    fun `getTimelineEvents » not all events in store » fetch missing events by filling gaps when minSize not reached`() =
        runTest {
            notAllRequestedEventsInStoreSetup()
            val result = async {
                cut.getTimelineEvents(room, event3.id) {
                    minSize = 3
                    maxSize = 4
                }.toList().map { it.first() }
            }
            timelineEventHandlerMock.unsafeFillTimelineGaps.first { it }
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent3,
                    timelineEvent2.copy(gap = null, previousEventId = event0.id),
                    timelineEvent0,
                    timelineEvent1.copy(gap = null, nextEventId = event0.id)
                )
            )
            result.await() shouldBe listOf(
                timelineEvent3,
                timelineEvent2.copy(gap = null, previousEventId = event0.id),
                timelineEvent0,
                timelineEvent1.copy(gap = null, nextEventId = event0.id)
            )
        }

    @Test
    fun `getTimelineEvents » not all events in store » not fetch when minSize reached`() = runTest {
        notAllRequestedEventsInStoreSetup()
        cut.getTimelineEvents(room, event3.id) { minSize = 2 }.toList().map { it.first() } shouldBe listOf(
            timelineEvent3,
            timelineEvent2.copy(gap = TimelineEvent.Gap.GapBefore("before-2")),
        )
        timelineEventHandlerMock.unsafeFillTimelineGaps.value shouldBe false
    }

    @Test
    fun `getTimelineEvents » complete timeline in store » flow should be finished when all collected`() =
        runTest {
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent1.copy(gap = null, previousEventId = null, event = createEvent),
                    timelineEvent2,
                    timelineEvent3
                )
            )
            cut.getTimelineEvents(room, event3.id)
                .toList().map { it.first() } shouldBe listOf(
                timelineEvent3,
                timelineEvent2,
                timelineEvent1.copy(gap = null, previousEventId = null, event = createEvent)
            )
        }


    @Test
    fun `getTimelineEvents » room upgrades » follow room upgrade from old to new room`() = runTest {
        roomUpgradesSetup()
        cut.getTimelineEvents(room, createEvent1.id, FORWARDS) { minSize = 5 }
            .take(5).toList().map { it.first() } shouldBe timeline
    }

    @Test
    fun `getTimelineEvents » room upgrades » follow room upgrade from new to old room`() = runTest {
        roomUpgradesSetup()
        cut.getTimelineEvents(newRoom, event3.id, BACKWARDS) { minSize = 5 }
            .take(5).toList().map { it.first() } shouldBe timeline.reversed()
    }

    @Test
    fun `getTimelineEvents » toFlowList » transform to list`() = runTest {
        roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
        val size = MutableStateFlow(2)
        val resultList = MutableStateFlow<List<TimelineEvent>?>(null)
        backgroundScope.launch {
            cut.getTimelineEvents(room, event3.id)
                .toFlowList(size)
                .collectLatest { it1 -> resultList.value = it1.map { it.first() } }
        }
        resultList.first { it?.size == 2 } shouldBe listOf(
            timelineEvent3,
            timelineEvent2
        )
        size.value = 3
        resultList.first { it?.size == 3 } shouldBe listOf(
            timelineEvent3,
            timelineEvent2,
            timelineEvent1
        )
    }

    @Test
    fun `getTimelineEvents » get timeline events around » get the event '2' and it's predecessor and successor as flow`() =
        runTest {
            getTimelineEventsAroundSetup()
            val maxSizeBefore = MutableStateFlow(1)
            val maxSizeAfter = MutableStateFlow(1)
            val result = MutableStateFlow<List<TimelineEvent>?>(null)
            backgroundScope.launch {
                cut.getTimelineEventsAround(
                    room,
                    event2.id,
                    maxSizeBefore = maxSizeBefore,
                    maxSizeAfter = maxSizeAfter
                )
                    .collect { events -> result.value = events.map { it.first() } }
            }

            result.first { it?.size == 3 } shouldBe listOf(
                newTimelineEvent1,
                timelineEvent2,
                newTimelineEvent3,
            )

            maxSizeBefore.value = 2
            result.first { it?.size == 3 } shouldBe listOf(
                newTimelineEvent1,
                timelineEvent2,
                newTimelineEvent3,
            )

            maxSizeAfter.value = 2
            result.first { it?.size == 4 } shouldBe listOf(
                newTimelineEvent1,
                timelineEvent2,
                newTimelineEvent3,
                timelineEvent4,
            )
        }

    @Test
    fun `getTimelineEvents » get timeline events around » get the event '2' and it's predecessor and successor`() =
        runTest {
            getTimelineEventsAroundSetup()

            with(cut) {
                getTimelineEventsAround(
                    room,
                    event2.id,
                    configBefore = { maxSize = 2 },
                    configAfter = { maxSize = 2 })
                    .map { it.first() } shouldBe listOf(
                    newTimelineEvent1,
                    timelineEvent2,
                    newTimelineEvent3,
                )

                getTimelineEventsAround(
                    room,
                    event2.id,
                    configBefore = { maxSize = 3 },
                    configAfter = { maxSize = 2 })
                    .map { it.first() } shouldBe listOf(
                    newTimelineEvent1,
                    timelineEvent2,
                    newTimelineEvent3,
                )

                getTimelineEventsAround(
                    room,
                    event2.id,
                    configBefore = { maxSize = 3 },
                    configAfter = { maxSize = 3 })
                    .map { it.first() } shouldBe listOf(
                    newTimelineEvent1,
                    timelineEvent2,
                    newTimelineEvent3,
                    timelineEvent4,
                )
            }
        }


    @Test
    fun `getLastTimelineEvents » get timeline events`() = runTest {
        getLastTimelineEventsSetup()
        roomStore.update(room) { Room(roomId = room, lastEventId = event3.id) }
        cut.getLastTimelineEvents(room)
            .first()
            .shouldNotBeNull()
            .take(3).toList().map { it.first() } shouldBe listOf(
            timelineEvent3,
            timelineEvent2,
            timelineEvent1
        )
    }

    @Test
    fun `getLastTimelineEvents » cancel old timeline event flow`() = runTest {
        getLastTimelineEventsSetup()
        roomStore.update(room) { Room(roomId = room, lastEventId = event2.id) }
        val collectedEvents = MutableStateFlow<List<TimelineEvent?>?>(null)
        backgroundScope.launch {
            cut.getLastTimelineEvents(room)
                .filterNotNull()
                .collectLatest { timelineEventFlow ->
                    collectedEvents.value = timelineEventFlow.take(2).toList().map { it.first() }
                }
        }

        collectedEvents.first { it?.size == 2 }
        collectedEvents.value shouldBe listOf(
            timelineEvent2,
            timelineEvent1,
        )

        roomStore.update(room) { Room(roomId = room, lastEventId = event3.id) }
        collectedEvents.first { it?.first()?.eventId == event3.id }
        collectedEvents.value shouldBe listOf(
            timelineEvent3,
            timelineEvent2,
        )
    }

    @Test
    fun `getLastTimelineEvents » transform to list`() = runTest {
        getLastTimelineEventsSetup()
        val size = MutableStateFlow(2)
        val resultList = MutableStateFlow<List<TimelineEvent>?>(null)

        roomStore.update(room) { Room(roomId = room, lastEventId = event2.id) }
        backgroundScope.launch {
            cut.getLastTimelineEvents(room)
                .toFlowList(size)
                .collectLatest { it1 -> resultList.value = it1.map { it.first() } }
        }

        resultList.first { it?.size == 2 } shouldBe listOf(
            timelineEvent2,
            timelineEvent1,
        )

        roomStore.update(room) { Room(roomId = room, lastEventId = event3.id) }
        size.value = 1
        resultList.first { it?.size == 1 && it.first().eventId == event3.id } shouldBe listOf(
            timelineEvent3
        )

        size.value = 3
        resultList.first { it?.size == 3 } shouldBe listOf(
            timelineEvent3,
            timelineEvent2,
            timelineEvent1
        )
    }

    @Test
    fun `getTimelineEventsFromNowOn » get timeline events from now on`() = runTest {
        val event10 = MessageEvent(
            RoomMessageEventContent.TextBased.Text("hi"),
            EventId("\$event10"),
            sender,
            room,
            10
        )
        syncBatchTokenStore.setSyncBatchToken("token1")
        roomTimelineStore.addAll(
            listOf(
                timelineEvent1,
                timelineEvent1.copy(event = event1.copy(id = event10.id, roomId = RoomId("!other:server")))
            )
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0, since = "token1")) {
                Sync.Response(
                    nextBatch = "nextBatch1", room = Sync.Response.Rooms(
                        join = mapOf(
                            room to Sync.Response.Rooms.JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(event1)
                                )
                            )
                        )
                    )
                )
            }
            matrixJsonEndpoint(Sync(timeout = 0, since = "nextBatch1")) {
                Sync.Response(
                    nextBatch = "nextBatch2", room = Sync.Response.Rooms(
                        join = mapOf(
                            RoomId("!other:server") to Sync.Response.Rooms.JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(event10)
                                )
                            )
                        )
                    )
                )
            }
        }
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            cut.getTimelineEventsFromNowOn(decryptionTimeout = 0.seconds).take(2).toList()
        }
        delay(100.milliseconds)
        api.sync.startOnce().getOrThrow()
        api.sync.startOnce().getOrThrow()
        result.await().map { it.eventId } shouldBe listOf(event1.id, event10.id)
    }

    private suspend fun allRequestedEventsInStoreSetup() {
        roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
    }

    private suspend fun notAllRequestedEventsInStoreSetup() {
        roomTimelineStore.addAll(
            listOf(
                timelineEvent1.copy(gap = TimelineEvent.Gap.GapAfter("after-1")),
                timelineEvent2.copy(gap = TimelineEvent.Gap.GapBefore("before-2")),
                timelineEvent3
            )
        )
    }

    private suspend fun roomUpgradesSetup() {
        roomTimelineStore.addAll(timeline)
        with(roomStateStore) {
            save(tombstoneEvent)
            save(createEvent1)
            save(createEvent2)
        }
    }

    private suspend fun getTimelineEventsAroundSetup() {
        roomTimelineStore.addAll(
            listOf(
                newTimelineEvent1,
                timelineEvent2,
                newTimelineEvent3,
                timelineEvent4
            )
        )
    }


    private suspend fun getLastTimelineEventsSetup() {
        roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
    }
}