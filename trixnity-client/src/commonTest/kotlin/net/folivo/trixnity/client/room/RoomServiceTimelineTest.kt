package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.Direction.FORWARD
import net.folivo.trixnity.clientserverapi.model.rooms.GetEventsResponse
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key

class RoomServiceTimelineTest : ShouldSpec({
    val room = RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixClientServerApiClient>()
    val olm = mockk<OlmService>()
    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        cut = RoomService(UserId("alice", "server"), store, api, olm, mockk(), mockk(), mockk())
    }

    afterTest {
        clearAllMocks()
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
                    store.room.update(room) {
                        Room(
                            roomId = room,
                            lastMessageEventAt = Instant.fromEpochMilliseconds(0),
                            lastEventId = null
                        )
                    }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.room.update(room) {
                        Room(
                            roomId = room,
                            lastMessageEventAt = Instant.fromEpochMilliseconds(0),
                            lastEventId = null
                        )
                    }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId should beNull()
                        gap shouldBe GapBoth("previous")
                    }
                }
            }
            context("with previous events") {
                should("add elements to timeline") {
                    store.room.update(room) {
                        Room(
                            roomId = room,
                            lastMessageEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                            lastEventId = event1.id
                        )
                    }
                    store.roomTimeline.addAll(
                        listOf(
                            TimelineEvent(
                                event = event1,
                                roomId = room,
                                eventId = event1.id,
                                previousEventId = null,
                                nextEventId = null,
                                gap = GapAfter("oldPrevious")
                            )
                        )
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.room.update(room) {
                        Room(
                            roomId = room,
                            lastMessageEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                            lastEventId = event1.id
                        )
                    }
                    store.roomTimeline.addAll(
                        listOf(
                            TimelineEvent(
                                event = event1,
                                roomId = room,
                                eventId = event1.id,
                                previousEventId = null,
                                nextEventId = null,
                                gap = GapAfter("oldPrevious")
                            )
                        )
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", true)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId should beNull()
                        gap shouldBe GapBoth("previous")
                    }
                }
            }
        }
        context("without gap") {
            should("add elements to timeline") {
                store.room.update(room) {
                    Room(
                        roomId = room,
                        lastMessageEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                        lastEventId = event1.id
                    )
                }
                store.roomTimeline.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = null,
                            gap = GapAfter("oldPrevious")
                        )
                    )
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", false)
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    eventId shouldBe event1.id
                    roomId shouldBe room
                    previousEventId should beNull()
                    nextEventId shouldBe event2.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                    event shouldBe event2
                    eventId shouldBe event2.id
                    roomId shouldBe room
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    eventId shouldBe event3.id
                    roomId shouldBe room
                    previousEventId shouldBe event2.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
            should("add one element to timeline") {
                store.room.update(room) {
                    Room(
                        roomId = room,
                        lastMessageEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                        lastEventId = event1.id
                    )
                }
                store.roomTimeline.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = null,
                            gap = GapAfter("oldPrevious")
                        )
                    )
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", false)
                assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                    event shouldBe event1
                    eventId shouldBe event1.id
                    roomId shouldBe room
                    previousEventId should beNull()
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                    event shouldBe event3
                    eventId shouldBe event3.id
                    roomId shouldBe room
                    previousEventId shouldBe event1.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
        }
        context("outbox messages") {
            should("be used to instantly decrypt received encrypted timeline events that have same transaction id") {
                store = spyk(InMemoryStore(storeScope).apply { init() })
                cut = RoomService(UserId("alice", "server"), store, api, olm, mockk(), mockk(), mockk())
                store.room.update(room) {
                    Room(
                        roomId = room,
                        lastMessageEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                        lastEventId = event1.id
                    )
                }
                coEvery { store.roomOutboxMessage.get("transactionId1") } returns RoomOutboxMessage(
                    "transactionId1",
                    room,
                    TextMessageEventContent("Hello!")
                )
                coEvery { store.roomOutboxMessage.get("transactionId2") } returns null
                coEvery { store.roomOutboxMessage.get("transactionId-unknown") } returns null
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
                    decryptedEvent shouldBe Result.success(Event.MegolmEvent(TextMessageEventContent("Hello!"), room))
                }
                assertSoftly(store.roomTimeline.get(eventId2, room)!!) {
                    decryptedEvent shouldBe null
                }
                assertSoftly(store.roomTimeline.get(eventId3, room)!!) {
                    decryptedEvent shouldBe null
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
        context("start event has previous gap") {
            should("do nothing when no previous batch") {
                cut.fetchMissingEvents(
                    TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = null
                    )
                )
            }
            context("no previous event") {
                should("add elements to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = null,
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2, event1),
                            state = listOf()
                        )
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("add one element to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = null,
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2),
                            state = listOf()
                        )
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("detect start of timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = null,
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "start",
                            chunk = listOf(),
                            state = listOf()
                        )
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBefore("start")
                    )
                    store.roomTimeline.addAll(listOf(startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe beNull()
                        nextEventId should beNull()
                        gap should beNull()
                    }
                }
            }
            context("gap filled") {
                should("add element to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2),
                            state = listOf()
                        )
                    )
                    val previousEvent = TimelineEvent(
                        event = event1,
                        roomId = room,
                        eventId = event1.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("ignore overlapping events") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2, event1.copy(originTimestamp = 24)),
                            state = listOf()
                        )
                    )
                    val previousEvent = TimelineEvent(
                        event = event1,
                        roomId = room,
                        eventId = event1.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
            }
            context("gap not filled") {
                should("add element to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "next",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2),
                            state = listOf()
                        )
                    )
                    val previousEvent = TimelineEvent(
                        event = event1,
                        roomId = room,
                        eventId = event1.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("next")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = event1.id,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.roomTimeline.addAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event1.id, room)!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBoth("next")
                    }
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
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
                    cut.fetchMissingEvents(
                        TimelineEvent(
                            event = event3,
                            roomId = room,
                            eventId = event3.id,
                            previousEventId = null,
                            nextEventId = null,
                            gap = GapAfter("something")
                        )
                    )
                }
            }
            context("gap filled") {
                should("add elements to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = FORWARD,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event3, event4),
                            state = listOf()
                        )
                    )
                    val nextEvent = TimelineEvent(
                        event = event5,
                        roomId = room,
                        eventId = event5.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event2,
                        roomId = room,
                        eventId = event2.id,
                        previousEventId = event1.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        eventId shouldBe event5.id
                        roomId shouldBe room
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("end")
                    }
                }
                should("ignore overlapping events") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = FORWARD,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event4, event5.copy(originTimestamp = 24)),
                            state = listOf()
                        )
                    )
                    val nextEvent = TimelineEvent(
                        event = event5,
                        roomId = room,
                        eventId = event5.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("end")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = event2.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        eventId shouldBe event5.id
                        roomId shouldBe room
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("end")
                    }
                }
            }
            context("gap not filled") {
                should("add element to timeline") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "next",
                            dir = FORWARD,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns Result.success(
                        GetEventsResponse(
                            start = "start",
                            end = "end",
                            chunk = listOf(event4),
                            state = listOf()
                        )
                    )
                    val nextEvent = TimelineEvent(
                        event = event5,
                        roomId = room,
                        eventId = event5.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("next")
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = event2.id,
                        nextEventId = event5.id,
                        gap = GapAfter("start")
                    )
                    store.roomTimeline.addAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.roomTimeline.get(event3.id, room)!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.roomTimeline.get(event4.id, room)!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap shouldBe GapAfter("end")
                    }
                    assertSoftly(store.roomTimeline.get(event5.id, room)!!) {
                        event shouldBe event5
                        eventId shouldBe event5.id
                        roomId shouldBe room
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapBoth("next")
                    }
                }
            }
        }
    }
})