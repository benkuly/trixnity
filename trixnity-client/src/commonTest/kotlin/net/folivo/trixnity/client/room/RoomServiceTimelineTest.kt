package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.room.RoomService.Companion.LAZY_LOAD_MEMBERS_FILTER
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEventContext
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class RoomServiceTimelineTest : ShouldSpec({
    val room = RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
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
            MatrixClientConfiguration()
        )
    }

    afterTest {
        storeScope.cancel()
    }

    fun textEvent(i: Long = 24): MessageEvent<TextMessageEventContent> {
        return MessageEvent(
            TextMessageEventContent("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }
    context(RoomService::addEventsToTimelineAtEnd.name) {
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event3 = textEvent(3)
        context("with gap") {
            context("without previous events") {
                should("add elements to timeline") {
                    store.room.update(room) { Room(roomId = room, lastEventId = null) }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.room.update(room) { Room(roomId = room, lastEventId = null) }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId should beNull()
                        gap shouldBe GapBoth("previous")
                    }
                }
            }
            context("with previous events") {
                should("add elements to timeline") {
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
                        listOf(
                            TimelineEvent(
                                event = event1,
                                previousEventId = null,
                                nextEventId = null,
                                gap = GapAfter("oldPrevious")
                            )
                        )
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
                        listOf(
                            TimelineEvent(
                                event = event1,
                                previousEventId = null,
                                nextEventId = null,
                                gap = GapAfter("oldPrevious")
                            )
                        )
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event1.id
                        nextEventId should beNull()
                        gap shouldBe GapBoth("previous")
                    }
                }
            }
        }
        context("without gap") {
            should("add elements to timeline") {
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            previousEventId = null,
                            nextEventId = null,
                            gap = GapAfter("oldPrevious")
                        )
                    )
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", false)
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId should beNull()
                    nextEventId shouldBe event2.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe event2.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
            should("add one element to timeline") {
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            previousEventId = null,
                            nextEventId = null,
                            gap = GapAfter("oldPrevious")
                        )
                    )
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", false)
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId should beNull()
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe event1.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
        }
        context("outbox messages") {
            should("be used to instantly decrypt received encrypted timeline events that have same transaction id") {
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomOutboxMessage.update("transactionId1") {
                    RoomOutboxMessage(
                        "transactionId1",
                        room,
                        TextMessageEventContent("Hello!")
                    )
                }
                val eventId1 = EventId("\$event1")
                val eventId2 = EventId("\$event2")
                val eventId3 = EventId("\$event3")
                val encryptedEvent1 = MessageEvent(
                    MegolmEncryptedEventContent("foobar", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId1,
                    UserId("sender", "server"),
                    room,
                    0L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId1")
                )
                val encryptedEvent2 = MessageEvent(
                    MegolmEncryptedEventContent("barfoo", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId2,
                    UserId("other", "server"),
                    room,
                    10L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId2")
                )
                val encryptedEvent3 = MessageEvent(
                    MegolmEncryptedEventContent("foo", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId3,
                    UserId("sender", "server"),
                    room,
                    20L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId-unknown")
                )
                cut.addEventsToTimelineAtEnd(
                    room,
                    listOf(encryptedEvent1, encryptedEvent2, encryptedEvent3),
                    "previous",
                    false
                )

                assertSoftly(store.roomTimeline.get(eventId1, room)!!) {
                    content shouldBe Result.success(TextMessageEventContent("Hello!"))
                }
                assertSoftly(store.roomTimeline.get(eventId2, room)!!) {
                    content shouldBe null
                }
                assertSoftly(store.roomTimeline.get(eventId3, room)!!) {
                    content shouldBe null
                }
            }
        }
    }
    context(RoomService::fetchMissingEvents.name) {
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event3 = textEvent(3)
        val event4 = textEvent(4)
        val event5 = textEvent(5)
        context("start event does not exist in store") {
            should("save start event") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEventContext(
                            room.e(),
                            event3.id.e(),
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEventContext.Response(
                            start = "start",
                            end = "end",
                            event = event3
                        )
                    }
                }
                cut.fetchMissingEvents(event3.id, room).getOrThrow()
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe beNull()
                    nextEventId should beNull()
                    gap shouldBe GapBoth("start")
                }
            }
            should("add events before") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEventContext(
                            room.e(),
                            event3.id.e(),
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEventContext.Response(
                            start = "start",
                            end = "end",
                            event = event3,
                            eventsBefore = listOf(event2, event1)
                        )
                    }
                }
                cut.fetchMissingEvents(event3.id, room).getOrThrow()
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId should beNull()
                    nextEventId shouldBe event2.id
                    gap shouldBe GapBefore("start")
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe event2.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("end")
                }
            }
            should("add events after") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEventContext(
                            room.e(),
                            event3.id.e(),
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEventContext.Response(
                            start = "start",
                            end = "end",
                            event = event3,
                            eventsAfter = listOf(event2, event1)
                        )
                    }
                }
                cut.fetchMissingEvents(event3.id, room).getOrThrow()
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId shouldBe event2.id
                    nextEventId shouldBe beNull()
                    gap shouldBe GapAfter("end")
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    previousEventId shouldBe event3.id
                    nextEventId shouldBe event1.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe beNull()
                    nextEventId shouldBe event2.id
                    gap shouldBe GapBefore("start")
                }
            }
            should("add events before and after") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEventContext(
                            room.e(),
                            event3.id.e(),
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEventContext.Response(
                            start = "start",
                            end = "end",
                            event = event3,
                            eventsBefore = listOf(event1),
                            eventsAfter = listOf(event2)
                        )
                    }
                }
                cut.fetchMissingEvents(event3.id, room).getOrThrow()
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId shouldBe beNull()
                    nextEventId shouldBe event3.id
                    gap shouldBe GapBefore("start")
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event2.id
                    gap shouldBe beNull()
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    previousEventId shouldBe event3.id
                    nextEventId shouldBe beNull()
                    gap shouldBe GapAfter("end")
                }
            }
            should("fill gaps (find existing timeline)") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEventContext(
                            room.e(),
                            event3.id.e(),
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEventContext.Response(
                            start = "start",
                            end = "end",
                            event = event3,
                            eventsBefore = listOf(event2, event1),
                            eventsAfter = listOf(event4, event5)
                        )
                    }
                }
                val previousEvent = TimelineEvent(
                    event = event1,
                    previousEventId = null,
                    nextEventId = null,
                    gap = GapAfter("gap-previous")
                )
                val nextEvent = TimelineEvent(
                    event = event5,
                    previousEventId = null,
                    nextEventId = null,
                    gap = GapBefore("gap-next")
                )
                store.roomTimeline.addAll(listOf(previousEvent, nextEvent))
                cut.fetchMissingEvents(event3.id, room).getOrThrow()
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    previousEventId shouldBe beNull()
                    nextEventId shouldBe event2.id
                    gap shouldBe beNull()
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                    gap shouldBe beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    previousEventId shouldBe event2.id
                    nextEventId shouldBe event4.id
                    gap shouldBe beNull()
                }
                assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                    event shouldBe event4
                    previousEventId shouldBe event3.id
                    nextEventId shouldBe event5.id
                    gap shouldBe beNull()
                }
                assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                    event shouldBe event5
                    previousEventId shouldBe event4.id
                    nextEventId shouldBe beNull()
                    gap shouldBe beNull()
                }
            }
        }
        context("start event has previous gap") {
            context("no previous event") {
                should("add elements to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2, event1),
                                state = listOf()
                            )
                        }
                    }
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("add one element to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2),
                                state = listOf()
                            )
                        }
                    }
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("detect start of timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "start",
                                chunk = listOf(),
                                state = listOf()
                            )
                        }
                    }
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBefore("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe beNull()
                        nextEventId should beNull()
                        gap should beNull()
                    }
                }
            }
            context("gap filled") {
                should("add element to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "end",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2),
                                state = listOf()
                            )
                        }
                    }
                    val previousEvent = TimelineEvent(
                        event = event1,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("ignore overlapping events") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "end",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2, event1.copy(originTimestamp = 24)),
                                state = listOf()
                            )
                        }
                    }
                    val previousEvent = TimelineEvent(
                        event = event1,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
            }
            context("gap not filled") {
                should("add element to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "next",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2),
                                state = listOf()
                            )
                        }
                    }
                    val previousEvent = TimelineEvent(
                        event = event1,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("next")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBoth("next")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
            }
        }
        context("start event has next gap") {
            context("not an event with next events, we can fetch") {
                should("do nothing when no next event") {
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    store.roomTimeline.get(event3.id, room) shouldBe startEvent
                }
            }
            context("gap filled") {
                should("add elements to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "end",
                                dir = FORWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event3, event4),
                                state = listOf()
                            )
                        }
                    }
                    val nextEvent = TimelineEvent(
                        event = event5,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event2,
                        previousEventId = event1.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(event2.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("end")
                    }
                }
                should("ignore overlapping events") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "end",
                                dir = FORWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event4, event5.copy(originTimestamp = 24)),
                                state = listOf()
                            )
                        }
                    }
                    val nextEvent = TimelineEvent(
                        event = event5,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event2.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("end")
                    }
                }
            }
            context("gap not filled") {
                should("add element to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                "next",
                                dir = FORWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event4),
                                state = listOf()
                            )
                        }
                    }
                    val nextEvent = TimelineEvent(
                        event = event5,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("next")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        previousEventId = event2.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(event3.id, room).getOrThrow()
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap shouldBe GapAfter("end")
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapBoth("next")
                    }
                }
            }
        }
    }
})