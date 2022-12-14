package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventDecryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.seconds

class RoomServiceTimelineUtilsTest : ShouldSpec({
    timeout = 5_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var roomEventDecryptionServiceMock: RoomEventDecryptionServiceMock
    lateinit var timelineEventHandlerMock: TimelineEventHandlerMock
    val json = createMatrixEventJson()
    val contentMappings = DefaultEventContentSerializerMappings
    val currentSyncState = MutableStateFlow(SyncState.RUNNING)
    val userInfo = UserInfo(UserId("thisUser"), "deviceId", Key.Ed25519Key(value = ""), Key.Curve25519Key(value = ""))

    lateinit var cut: RoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)

        mediaServiceMock = MediaServiceMock()
        roomEventDecryptionServiceMock = RoomEventDecryptionServiceMock()
        timelineEventHandlerMock = TimelineEventHandlerMock()
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        cut = RoomServiceImpl(
            api,
            roomStore, roomStateStore, roomAccountDataStore, roomTimelineStore, roomOutboxMessageStore,
            listOf(roomEventDecryptionServiceMock),
            mediaServiceMock,
            timelineEventHandlerMock,
            CurrentSyncState(currentSyncState),
            userInfo,
            scope
        )
    }

    afterTest {
        scope.cancel()
    }

    fun encryptedEvent(i: Long = 24): MessageEvent<MegolmEncryptedEventContent> {
        return MessageEvent(
            MegolmEncryptedEventContent(
                ciphertext = "cipher $i",
                deviceId = "deviceId",
                sessionId = "senderId",
                senderKey = Key.Curve25519Key(value = "key")
            ),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }

    val event1 = encryptedEvent(1)
    val event2 = encryptedEvent(2)
    val event3 = encryptedEvent(3)
    val timelineEvent1 = TimelineEvent(
        event = event1,
        roomId = room,
        eventId = event1.id,
        previousEventId = null,
        nextEventId = event2.id,
        gap = TimelineEvent.Gap.GapBefore("1")
    )
    val timelineEvent2 = TimelineEvent(
        event = event2,
        roomId = room,
        eventId = event2.id,
        previousEventId = event1.id,
        nextEventId = event3.id,
        gap = null
    )
    val timelineEvent3 = TimelineEvent(
        event = event3,
        roomId = room,
        eventId = event3.id,
        previousEventId = event2.id,
        nextEventId = null,
        gap = TimelineEvent.Gap.GapAfter("3")
    )
    context(RoomServiceImpl::getTimelineEvents.name) {
        context("all requested events in store") {
            beforeTest {
                roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            }
            should("get timeline events backwards") {
                cut.getTimelineEvents(event3.id, room)
                    .take(3).toList().map { it.first() } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1
                )
            }
            should("get timeline events forwards") {
                cut.getTimelineEvents(event1.id, room, FORWARDS)
                    .take(3).toList().map { it.first() } shouldBe listOf(
                    timelineEvent1,
                    timelineEvent2,
                    timelineEvent3
                )
            }
            should("get timeline events with maxSize") {
                cut.getTimelineEvents(event1.id, room, FORWARDS, maxSize = 2)
                    .toList().map { it.first() } shouldBe listOf(
                    timelineEvent1,
                    timelineEvent2,
                )
            }
        }
        context("not all events in store") {
            val event0 = encryptedEvent(0)
            val timelineEvent0 = TimelineEvent(
                event = event0,
                roomId = room,
                eventId = event0.id,
                previousEventId = event1.id,
                nextEventId = event2.id,
                gap = null
            )
            beforeTest {
                roomTimelineStore.addAll(
                    listOf(
                        timelineEvent1.copy(gap = TimelineEvent.Gap.GapAfter("after-1")),
                        timelineEvent2.copy(gap = TimelineEvent.Gap.GapBefore("before-2")),
                        timelineEvent3
                    )
                )
            }
            should("fetch missing events by filling gaps") {
                val result = async {
                    cut.getTimelineEvents(event3.id, room).take(4).toList().map { it.first() }
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
            should("fetch missing events by filling gaps when minSize not reached") {
                val result = async {
                    cut.getTimelineEvents(event3.id, room, minSize = 3, maxSize = 4).toList().map { it.first() }
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
            should("not fetch when minSize reached") {
                cut.getTimelineEvents(event3.id, room, minSize = 2).toList().map { it.first() } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2.copy(gap = TimelineEvent.Gap.GapBefore("before-2")),
                )
                timelineEventHandlerMock.unsafeFillTimelineGaps.value shouldBe false
            }
        }
        context("complete timeline in store") {
            beforeTest {
                roomTimelineStore.addAll(
                    listOf(
                        timelineEvent1.copy(gap = null, previousEventId = null),
                        timelineEvent2,
                        timelineEvent3
                    )
                )
            }
            should("flow should be finished when all collected") {
                cut.getTimelineEvents(event3.id, room)
                    .toList().map { it.first() } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1.copy(gap = null, previousEventId = null)
                )
            }
        }
        context("toFlowList") {
            beforeTest {
                roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            }
            should("transform to list") {
                val size = MutableStateFlow(2)
                val resultList = MutableStateFlow<List<TimelineEvent>?>(null)
                val job = scope.launch {
                    cut.getTimelineEvents(event3.id, room)
                        .toFlowList(size)
                        .collectLatest { it1 -> resultList.value = it1.mapNotNull { it.first() } }
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
                job.cancel()
            }
        }
        context("get timeline events around") {
            val newEvent3 = encryptedEvent(3)
            val event4 = encryptedEvent(4)
            val newTimelineEvent1 = timelineEvent1.copy(gap = null)
            val newTimelineEvent3 = TimelineEvent(
                event = newEvent3,
                roomId = room,
                eventId = newEvent3.id,
                previousEventId = event2.id,
                nextEventId = event4.id,
                gap = null
            )
            val timelineEvent4 = TimelineEvent(
                event = event4,
                roomId = room,
                eventId = event4.id,
                previousEventId = newEvent3.id,
                nextEventId = null,
                gap = null
            )
            beforeTest {
                roomTimelineStore.addAll(
                    listOf(
                        newTimelineEvent1,
                        timelineEvent2,
                        newTimelineEvent3,
                        timelineEvent4
                    )
                )
            }

            should("get the event '2', it's predecessor and successor as flow") {
                val maxSizeBefore = MutableStateFlow(1)
                val maxSizeAfter = MutableStateFlow(1)
                val result = MutableStateFlow<List<TimelineEvent>?>(null)
                val job = scope.launch {
                    cut.getTimelineEventsAround(
                        event2.id,
                        room,
                        maxSizeBefore = maxSizeBefore,
                        maxSizeAfter = maxSizeAfter
                    )
                        .collect { result.value = it.mapNotNull { it.first() } }
                }

                result.first { it?.size == 3 } shouldBe listOf(
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )

                maxSizeBefore.value = 2
                result.first { it?.size == 3 } shouldBe listOf(
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )

                maxSizeAfter.value = 2
                result.first { it?.size == 4 } shouldBe listOf(
                    timelineEvent4,
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )
                job.cancel()
            }

            should("get the event '2', it's predecessor and successor") {
                cut.getTimelineEventsAround(event2.id, room, maxSizeBefore = 2, maxSizeAfter = 2)
                    .mapNotNull { it.first() } shouldBe listOf(
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )

                cut.getTimelineEventsAround(event2.id, room, maxSizeBefore = 3, maxSizeAfter = 2)
                    .mapNotNull { it.first() } shouldBe listOf(
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )

                cut.getTimelineEventsAround(event2.id, room, maxSizeBefore = 3, maxSizeAfter = 3)
                    .mapNotNull { it.first() } shouldBe listOf(
                    timelineEvent4,
                    newTimelineEvent3,
                    timelineEvent2,
                    newTimelineEvent1,
                )
            }
        }
    }
    context(RoomServiceImpl::getLastTimelineEvents.name) {
        lateinit var localTestScope: CoroutineScope
        beforeTest {
            roomTimelineStore.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            localTestScope = CoroutineScope(Dispatchers.Default)
        }
        afterTest {
            localTestScope.cancel()
        }
        should("get timeline events") {
            roomStore.update(room) { Room(roomId = room, lastEventId = event3.id) }
            cut.getLastTimelineEvents(room)
                .first()
                .shouldNotBeNull()
                .take(3).toList().map { it.first() } shouldBe listOf(
                timelineEvent3,
                timelineEvent2,
                timelineEvent1
            )
            localTestScope.coroutineContext.job.children.count() shouldBe 0
        }
        should("cancel old timeline event flow") {
            roomStore.update(room) { Room(roomId = room, lastEventId = event2.id) }
            val collectedEvents = MutableStateFlow<List<TimelineEvent?>?>(null)
            val job = localTestScope.launch {
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
            job.cancelAndJoin()
            localTestScope.coroutineContext.job.children.count() shouldBe 0
        }
        should("transform to list") {
            val size = MutableStateFlow(2)
            val resultList = MutableStateFlow<List<TimelineEvent>?>(null)

            roomStore.update(room) { Room(roomId = room, lastEventId = event2.id) }
            val job = localTestScope.launch {
                cut.getLastTimelineEvents(room)
                    .toFlowList(size)
                    .collectLatest { it1 -> resultList.value = it1.mapNotNull { it.first() } }
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

            job.cancelAndJoin()
            localTestScope.coroutineContext.job.children.count() shouldBe 0
        }
    }
    context(RoomServiceImpl::getTimelineEventsFromNowOn.name) {
        should("get timeline events from now on") {
            val event10 = MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("hi"),
                EventId("\$event10"),
                UserId("sender", "server"),
                room,
                10
            )
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent1,
                    timelineEvent1.copy(eventId = event10.id, roomId = RoomId("other", "server"))
                )
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(json, contentMappings, Sync(timeout = 0)) {
                    Sync.Response(
                        nextBatch = "next", room = Sync.Response.Rooms(
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
                matrixJsonEndpoint(json, contentMappings, Sync(timeout = 0)) {
                    Sync.Response(
                        nextBatch = "next", room = Sync.Response.Rooms(
                            join = mapOf(
                                RoomId("other", "server") to Sync.Response.Rooms.JoinedRoom(
                                    timeline = Sync.Response.Rooms.Timeline(
                                        events = listOf(event10)
                                    )
                                )
                            )
                        )
                    )
                }
            }
            val result = async {
                cut.getTimelineEventsFromNowOn(decryptionTimeout = 0.seconds).take(2).toList()
            }
            api.sync.startOnce().getOrThrow()
            api.sync.startOnce().getOrThrow()
            result.await().map { it.eventId } shouldBe listOf(event1.id, event10.id)
        }
    }
})