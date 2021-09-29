package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.rooms.Direction.BACKWARDS
import net.folivo.trixnity.client.api.rooms.Direction.FORWARD
import net.folivo.trixnity.client.api.rooms.GetEventsResponse
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import org.kodein.log.LoggerFactory

class RoomManagerTimelineTest : ShouldSpec({
    val room = MatrixId.RoomId("room", "server")
    val store = InMemoryStore()
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmManager>()
    val cut = RoomManager(store, api, olm, mockk(), loggerFactory = LoggerFactory.default)

    afterTest {
        clearMocks(api)
        store.clear()
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
    context(RoomManager::addEventsToTimelineAtEnd.name) {
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event3 = textEvent(3)
        context("with gap") {
            context("without previous events") {
                should("add elements to timeline") {
                    store.rooms.update(room) {
                        Room(
                            roomId = room,
                            lastEventAt = Instant.fromEpochMilliseconds(0),
                            lastEventId = null
                        )
                    }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), "previous", true)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.rooms.update(room) {
                        Room(
                            roomId = room,
                            lastEventAt = Instant.fromEpochMilliseconds(0),
                            lastEventId = null
                        )
                    }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1), "previous", true)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
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
                    store.rooms.update(room) {
                        Room(
                            roomId = room,
                            lastEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                            lastEventId = event1.id
                        )
                    }
                    store.rooms.timeline.updateAll(
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
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("previous")
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("previous")
                    }
                }
                should("add one element to timeline") {
                    store.rooms.update(room) {
                        Room(
                            roomId = room,
                            lastEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                            lastEventId = event1.id
                        )
                    }
                    store.rooms.timeline.updateAll(
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
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapAfter("oldPrevious")
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
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
                store.rooms.update(room) {
                    Room(
                        roomId = room,
                        lastEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                        lastEventId = event1.id
                    )
                }
                store.rooms.timeline.updateAll(
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
                assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                    event shouldBe event1
                    eventId shouldBe event1.id
                    roomId shouldBe room
                    previousEventId should beNull()
                    nextEventId shouldBe event2.id
                    gap should beNull()
                }
                assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                    event shouldBe event2
                    eventId shouldBe event2.id
                    roomId shouldBe room
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                    event shouldBe event3
                    eventId shouldBe event3.id
                    roomId shouldBe room
                    previousEventId shouldBe event2.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
            should("add one element to timeline") {
                store.rooms.update(room) {
                    Room(
                        roomId = room,
                        lastEventAt = Instant.fromEpochMilliseconds(event1.originTimestamp),
                        lastEventId = event1.id
                    )
                }
                store.rooms.timeline.updateAll(
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
                assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                    event shouldBe event1
                    eventId shouldBe event1.id
                    roomId shouldBe room
                    previousEventId should beNull()
                    nextEventId shouldBe event3.id
                    gap should beNull()
                }
                assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                    event shouldBe event3
                    eventId shouldBe event3.id
                    roomId shouldBe room
                    previousEventId shouldBe event1.id
                    nextEventId should beNull()
                    gap shouldBe GapAfter("previous")
                }
            }
        }
    }
    context(RoomManager::fetchMissingEvents.name) {
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event2, event1),
                        state = listOf()
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.rooms.timeline.updateAll(listOf(startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event2),
                        state = listOf()
                    )
                    val startEvent = TimelineEvent(
                        event = event3,
                        roomId = room,
                        eventId = event3.id,
                        previousEventId = null,
                        nextEventId = null,
                        gap = GapBoth("start")
                    )
                    store.rooms.timeline.updateAll(listOf(startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event2),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("start")
                    }
                }
                should("should ignore overlapping events") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event2, event1.copy(originTimestamp = 24)),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event2),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(previousEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event1.id, room).value!!) {
                        event shouldBe event1
                        eventId shouldBe event1.id
                        roomId shouldBe room
                        previousEventId should beNull()
                        nextEventId shouldBe event2.id
                        gap shouldBe GapBoth("next")
                    }
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap shouldBe GapBefore("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event3, event4),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                        event shouldBe event2
                        eventId shouldBe event2.id
                        roomId shouldBe room
                        previousEventId shouldBe event1.id
                        nextEventId shouldBe event3.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event4.id, room).value!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event5.id, room).value!!) {
                        event shouldBe event5
                        eventId shouldBe event5.id
                        roomId shouldBe room
                        previousEventId shouldBe event4.id
                        nextEventId should beNull()
                        gap shouldBe GapAfter("end")
                    }
                }
                should("should ignore overlapping events") {
                    coEvery {
                        api.rooms.getEvents(
                            roomId = room,
                            from = "start",
                            to = "end",
                            dir = FORWARD,
                            limit = 20,
                            filter = """{"lazy_load_members":true}"""
                        )
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event4, event5.copy(originTimestamp = 24)),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event4.id, room).value!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event5.id, room).value!!) {
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
                    } returns GetEventsResponse(
                        start = "start",
                        end = "end",
                        chunk = listOf(event4),
                        state = listOf()
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
                    store.rooms.timeline.updateAll(listOf(nextEvent, startEvent))
                    cut.fetchMissingEvents(startEvent)
                    assertSoftly(store.rooms.timeline.byId(event3.id, room).value!!) {
                        event shouldBe event3
                        eventId shouldBe event3.id
                        roomId shouldBe room
                        previousEventId shouldBe event2.id
                        nextEventId shouldBe event4.id
                        gap should beNull()
                    }
                    assertSoftly(store.rooms.timeline.byId(event4.id, room).value!!) {
                        event shouldBe event4
                        eventId shouldBe event4.id
                        roomId shouldBe room
                        previousEventId shouldBe event3.id
                        nextEventId shouldBe event5.id
                        gap shouldBe GapAfter("end")
                    }
                    assertSoftly(store.rooms.timeline.byId(event5.id, room).value!!) {
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