package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test

class TimelineTest : TrixnityBaseTest() {
    private val roomId = RoomId("!room:server")

    private val roomServiceMock = RoomServiceMock()
    private val cut = TimelineImpl(roomService = roomServiceMock) { it }

    @Test
    fun `init » add events`() = runTest {
        roomServiceMock.returnGetTimelineEventsDisableDrop = true
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
        val expectedResult = listOf("1", "2", "3", "2", "1")
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe expectedResult
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe expectedResult
        state.isInitialized shouldBe true
    }

    @Test
    fun `init » not suspend infinite when no element before or after start`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")))
        val expectedResult = listOf("3")
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe expectedResult
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe expectedResult
        state.isInitialized shouldBe true
    }

    @Test
    fun `init » re-use events`() = runTest {
        roomServiceMock.returnGetTimelineEventsDisableDrop = true
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents =
            flowOf(
                flowOf(timelineEvent("3")),
                flowOf(timelineEvent("2")),
                flowOf(timelineEvent("1"))
            )
        cut.init(roomId, EventId("start"))
        roomServiceMock.returnGetTimelineEvents =
            flowOf(
                flowOf(timelineEvent("3")),
                flowOf(timelineEvent("2")),
                flowOf(timelineEvent("0"))
            )
        val change = cut.init(roomId, EventId("start"))
        change.elementsBeforeChange.map { it.first().eventId.full } shouldBe listOf("1", "2", "3", "2", "1")
        change.elementsAfterChange.map { it.first().eventId.full } shouldBe listOf("0", "2", "3", "2", "0")
        change.removedElements.map { it.first().eventId.full } shouldBe listOf("1", "1")
        change.addedElements.map { it.first().eventId.full } shouldBe listOf("0", "0")
    }

    @Test
    fun `loadBefore » load events before`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
        cut.loadBefore().addedElements.map { it.first().eventId.full } shouldBe listOf("1", "2")
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe listOf("1", "2", "3")
        state.isLoadingBefore shouldBe false
    }

    @Test
    fun `loadAfter » load events after`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
        cut.loadAfter().addedElements.map { it.first().eventId.full } shouldBe listOf("2", "1")
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe listOf("3", "2", "1")
        state.isLoadingAfter shouldBe false
    }

    @Test
    fun `dropBefore » drop events before`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))

        cut.loadBefore()

        cut.dropBefore(roomId, EventId("2")).removedElements.map { it.first().eventId.full } shouldBe listOf("1")
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe listOf("2", "3")
        state.isLoadingBefore shouldBe false
    }

    @Test
    fun `dropAfter » drop events after`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")
        roomServiceMock.returnGetTimelineEvents =
            flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))

        cut.loadAfter()

        cut.dropBefore(roomId, EventId("2")).removedElements.map { it.first().eventId.full } shouldBe listOf("3")
        val state = cut.state.first()
        state.elements.map { it.first().eventId.full } shouldBe listOf("2", "1")
        state.isLoadingBefore shouldBe false
    }

    @Test
    fun `canLoadBefore » be false when first event`() = runTest {
        val timelineEvent = timelineEvent("3")
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadBefore shouldBe false
    }

    @Test
    fun `canLoadBefore » be true when not first event`() = runTest {
        val timelineEvent = timelineEvent("3").copy(previousEventId = EventId("bla"))
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadBefore shouldBe true
    }

    @Test
    fun `canLoadBefore » be true when upgraded before`() = runTest {
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
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadBefore shouldBe true
    }

    @Test
    fun `canLoadAfter » be false when last event`() = runTest {
        val timelineEvent = timelineEvent("3")
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadAfter shouldBe false
    }

    @Test
    fun `canLoadAfter » be true when not last event`() = runTest {
        val timelineEvent = timelineEvent("3").copy(nextEventId = EventId("bla"))
        roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent))
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadAfter shouldBe true
    }

    @Test
    fun `canLoadAfter » be true when upgraded after`() = runTest {
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
        cut.init(roomId, EventId("start")).addedElements.map { it.first().eventId.full } shouldBe listOf("3")

        cut.state.first().canLoadAfter shouldBe true
    }

    private fun timelineEvent(id: String): TimelineEvent =
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
}