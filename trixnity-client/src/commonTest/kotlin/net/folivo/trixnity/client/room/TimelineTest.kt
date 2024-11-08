package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent

class TimelineTest : ShouldSpec({
    timeout = 5_000

    val roomId = RoomId("room", "server")
    lateinit var roomServiceMock: RoomServiceMock
    lateinit var cut: SimpleTimeline

    beforeTest {
        roomServiceMock = RoomServiceMock()
        cut = TimelineImpl(roomId, roomService = roomServiceMock) { it }
    }

    fun timelineEvent(id: String): TimelineEvent =
        TimelineEvent(
            event = MessageEvent(
                RoomMessageEventContent.TextBased.Text(id),
                EventId(id),
                UserId("sender", "server"),
                roomId,
                1234
            ),
            gap = null,
            nextEventId = null,
            previousEventId = null,
        )

    context(SimpleTimeline::init.name) {
        should("add events") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            val expectedResult = listOf("1", "2", "3", "2", "1")
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe expectedResult
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe expectedResult
            state.isInitialized shouldBe true
        }
        should("not suspend infinite, when no element before or after start") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")))
            val expectedResult = listOf("3")
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe expectedResult
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe expectedResult
            state.isInitialized shouldBe true
        }
    }
    context(SimpleTimeline::loadBefore.name) {
        should("load events before") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            cut.loadBefore().newElements.map { it.first().eventId.full } shouldBe listOf("1", "2")
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe listOf("1", "2", "3")
            state.isLoadingBefore shouldBe false
        }
    }
    context(SimpleTimeline::loadAfter.name) {
        should("load events after") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            cut.loadAfter().newElements.map { it.first().eventId.full } shouldBe listOf("2", "1")
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe listOf("3", "2", "1")
            state.isLoadingAfter shouldBe false
        }
    }
    context(SimpleTimeline::dropBefore.name) {
        should("drop events before") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))

            cut.loadBefore()

            cut.dropBefore(roomId, EventId("2")).removedElements.map { it.first().eventId.full } shouldBe listOf("1")
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe listOf("2", "3")
            state.isLoadingBefore shouldBe false
        }
    }
    context(SimpleTimeline::dropAfter.name) {
        should("drop events after") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))

            cut.loadAfter()

            cut.dropBefore(roomId, EventId("2")).removedElements.map { it.first().eventId.full } shouldBe listOf("3")
            val state = cut.state.first()
            state.elements.map { it.first().eventId.full } shouldBe listOf("2", "1")
            state.isLoadingBefore shouldBe false
        }
    }
    context(TimelineState<TimelineEvent>::canLoadBefore.name) {
        should("be false when first event") {
            val timelineEvent = timelineEvent("3")
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadBefore shouldBe false
        }
        should("be true when not first event") {
            val timelineEvent = timelineEvent("3").copy(previousEventId = EventId("bla"))
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadBefore shouldBe true
        }
        should("be true when upgraded before") {
            val timelineEvent = timelineEvent("3").copy(
                event = StateEvent(
                    CreateEventContent(predecessor = CreateEventContent.PreviousRoom(RoomId("bla"), EventId("bla"))),
                    EventId("3"),
                    UserId("sender", "server"),
                    roomId,
                    1,
                    stateKey = ""
                ), previousEventId = EventId("bla")
            )
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadBefore shouldBe true
        }
    }
    context(TimelineState<TimelineEvent>::canLoadAfter.name) {
        should("be false when last event") {
            val timelineEvent = timelineEvent("3")
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadAfter shouldBe false
        }
        should("be true when not last event") {
            val timelineEvent = timelineEvent("3").copy(nextEventId = EventId("bla"))
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadAfter shouldBe true
        }
        should("be true when upgraded after") {
            val timelineEvent = timelineEvent("3")
            roomServiceMock.state.value = mapOf(
                RoomServiceMock.GetStateKey(
                    timelineEvent.roomId,
                    TombstoneEventContent::class
                ) to StateEvent(
                    TombstoneEventContent("", RoomId("")),
                    EventId("3"),
                    UserId("sender", "server"),
                    roomId,
                    1,
                    stateKey = ""
                )
            )
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
            cut.init(EventId("start")).newElements.map { it.first().eventId.full } shouldBe listOf("3")

            cut.state.first().canLoadAfter shouldBe true
        }
    }
})