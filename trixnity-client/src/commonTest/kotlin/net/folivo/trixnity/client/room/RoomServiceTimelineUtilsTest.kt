package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.seconds

class RoomServiceTimelineUtilsTest : ShouldSpec({
    timeout = 5_000

    val room = RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var scope: CoroutineScope
    lateinit var localTestScope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()
    val contentMappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.RUNNING)

    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        scope = CoroutineScope(Dispatchers.Default)
        localTestScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        cut = RoomService(
            UserId("alice", "server"),
            store,
            api,
            OlmEventServiceMock(),
            KeyBackupServiceMock(),
            UserServiceMock(),
            MediaServiceMock(),
            currentSyncState,
            MatrixClientConfiguration(),
            scope,
        )
    }

    afterTest {
        storeScope.cancel()
        localTestScope.cancel()
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
    context(RoomService::getTimelineEvents.name) {
        context("all requested events in store") {
            beforeTest {
                store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            }
            should("get timeline events backwards") {
                cut.getTimelineEvents(event3.id, room)
                    .take(3).toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1
                )
            }
            should("get timeline events forwards") {
                cut.getTimelineEvents(event1.id, room, FORWARDS)
                    .take(3).toList().map { it.value } shouldBe listOf(
                    timelineEvent1,
                    timelineEvent2,
                    timelineEvent3
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
                store.roomTimeline.addAll(
                    listOf(
                        timelineEvent1.copy(gap = TimelineEvent.Gap.GapAfter("after-1")),
                        timelineEvent2.copy(gap = TimelineEvent.Gap.GapBefore("before-2")),
                        timelineEvent3
                    )
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json,
                        contentMappings,
                        GetEvents(
                            roomId = room.e(),
                            from = "before-2",
                            to = "after-1",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    ) {
                        GetEvents.Response(
                            start = "before-2",
                            end = "after-1",
                            chunk = listOf(event0),
                            state = listOf()
                        )
                    }
                }
            }
            should("fetch missing events from server") {
                cut.getTimelineEvents(event3.id, room)
                    .take(4).toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2.copy(gap = null, previousEventId = event0.id),
                    timelineEvent0,
                    timelineEvent1.copy(gap = null, nextEventId = event0.id)
                )
            }
        }
        context("complete timeline in store") {
            beforeTest {
                store.roomTimeline.addAll(
                    listOf(
                        timelineEvent1.copy(gap = null, previousEventId = null),
                        timelineEvent2,
                        timelineEvent3
                    )
                )
            }
            should("flow should be finished when all collected") {
                cut.getTimelineEvents(event3.id, room)
                    .toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1.copy(gap = null, previousEventId = null)
                )
            }
        }
        context("toList") {
            beforeTest {
                store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            }
            should("transform to list") {
                val size = MutableStateFlow(2)
                val resultList = MutableStateFlow<List<TimelineEvent>?>(null)
                localTestScope.launch {
                    cut.getTimelineEvents(event3.id, room)
                        .toFlowList(size)
                        .collectLatest { it1 -> resultList.value = it1.mapNotNull { it.value } }
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
        }
        context("get timeline events around") {
            val event3 = encryptedEvent(3)
            val event4 = encryptedEvent(4)
            val timelineEvent3 = TimelineEvent(
                event = event3,
                roomId = room,
                eventId = event3.id,
                previousEventId = event2.id,
                nextEventId = event4.id,
                gap = null
            )
            val timelineEvent4 = TimelineEvent(
                event = event4,
                roomId = room,
                eventId = event4.id,
                previousEventId = event3.id,
                nextEventId = null,
                gap = TimelineEvent.Gap.GapAfter("4")
            )
            beforeTest {
                store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3, timelineEvent4))
            }

            should("get the event '2', it's predecessor and successor") {
                val beforeInclusive = MutableStateFlow(2)
                val afterInclusive = MutableStateFlow(2)
                val result = MutableStateFlow<List<TimelineEvent>?>(null)
                localTestScope.launch {
                    cut.getTimelineEventsAround(event2.id, room, beforeInclusive, afterInclusive)
                        .collect { result.value = it.mapNotNull { it.value } }
                }

                result.first { it?.size == 3 } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1,
                )

                beforeInclusive.value = 3
                result.first { it?.size == 3 } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1,
                )

                afterInclusive.value = 3
                result.first { it?.size == 4 } shouldBe listOf(
                    timelineEvent4,
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1,
                )
            }
        }
    }
    context(RoomService::getLastTimelineEvents.name) {
        beforeTest {
            store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
        }
        should("get timeline events") {
            store.room.update(room) { Room(roomId = room, lastEventId = event3.id) }
            cut.getLastTimelineEvents(room)
                .first()
                .shouldNotBeNull()
                .take(3).toList().map { it.value } shouldBe listOf(
                timelineEvent3,
                timelineEvent2,
                timelineEvent1
            )
            localTestScope.coroutineContext.job.children.count() shouldBe 0
        }
        should("cancel old timeline event flow") {
            store.room.update(room) { Room(roomId = room, lastEventId = event2.id) }
            val collectedEvents = MutableStateFlow<List<TimelineEvent?>?>(null)
            val job = localTestScope.launch {
                cut.getLastTimelineEvents(room)
                    .filterNotNull()
                    .collectLatest { timelineEventFlow ->
                        collectedEvents.value = timelineEventFlow.take(2).toList().map { it.value }
                    }
            }

            collectedEvents.first { it?.size == 2 }
            collectedEvents.value shouldBe listOf(
                timelineEvent2,
                timelineEvent1,
            )

            store.room.update(room) { Room(roomId = room, lastEventId = event3.id) }
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

            store.room.update(room) { Room(roomId = room, lastEventId = event2.id) }
            val job = localTestScope.launch {
                cut.getLastTimelineEvents(room)
                    .toFlowList(size)
                    .collectLatest { it1 -> resultList.value = it1.mapNotNull { it.value } }
            }

            resultList.first { it?.size == 2 } shouldBe listOf(
                timelineEvent2,
                timelineEvent1,
            )

            store.room.update(room) { Room(roomId = room, lastEventId = event3.id) }
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
    context(RoomService::getTimelineEventsFromNowOn.name) {
        should("get timeline events from now on") {
            val event10 = MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("hi"),
                EventId("\$event10"),
                UserId("sender", "server"),
                room,
                10
            )
            store.roomTimeline.addAll(
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