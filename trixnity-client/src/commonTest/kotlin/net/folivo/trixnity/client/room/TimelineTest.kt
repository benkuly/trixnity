package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

class TimelineTest : ShouldSpec({
    timeout = 5_000

    lateinit var roomServiceMock: RoomServiceMock
    lateinit var cut: Timeline

    beforeTest {
        roomServiceMock = RoomServiceMock()
        cut = TimelineImpl(RoomId("room", "server"), roomService = roomServiceMock)
    }

    fun timelineEvent(id: String): TimelineEvent =
        TimelineEvent(
            event = Event.MessageEvent(
                RoomMessageEventContent.TextMessageEventContent(id),
                EventId(id),
                UserId("sender", "server"),
                RoomId("room", "server"),
                1234
            ),
            gap = null,
            nextEventId = null,
            previousEventId = null,
        )

    context(Timeline::init.name) {
        should("add events") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            val expectedResult = listOf("1", "2", "3", "2", "1")
            cut.init(EventId("start")).map { it.first().eventId.full } shouldBe expectedResult
            cut.events.first().map { it.first().eventId.full } shouldBe expectedResult
            cut.isInitialized.first() shouldBe true
            cut.lastLoadedEventIdBefore.first()?.full shouldBe "1"
            cut.lastLoadedEventIdAfter.first()?.full shouldBe "1"
        }
        should("not suspend infinite, when no element before or after start") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")))
            val expectedResult = listOf("3")
            cut.init(EventId("start")).map { it.first().eventId.full } shouldBe expectedResult
            cut.events.first().map { it.first().eventId.full } shouldBe expectedResult
            cut.isInitialized.first() shouldBe true
            cut.lastLoadedEventIdBefore.first()?.full shouldBe "3"
            cut.lastLoadedEventIdAfter.first()?.full shouldBe "3"
        }
    }
    context(Timeline::loadBefore.name) {
        should("load events before") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            cut.loadBefore().map { it.first().eventId.full } shouldBe listOf("1", "2")
            cut.events.first().map { it.first().eventId.full } shouldBe listOf("1", "2", "3")
            cut.lastLoadedEventIdBefore.first()?.full shouldBe "1"
            cut.lastLoadedEventIdAfter.first()?.full shouldBe "3"
            cut.isLoadingBefore.first() shouldBe false
        }
    }
    context(Timeline::loadAfter.name) {
        should("load events after") {
            roomServiceMock.returnGetTimelineEvent = flowOf(timelineEvent("3"))
            roomServiceMock.returnGetTimelineEvents = flowOf(flowOf(timelineEvent("3")))
            cut.init(EventId("start")).map { it.first().eventId.full } shouldBe listOf("3")
            roomServiceMock.returnGetTimelineEvents =
                flowOf(flowOf(timelineEvent("3")), flowOf(timelineEvent("2")), flowOf(timelineEvent("1")))
            cut.loadAfter().map { it.first().eventId.full } shouldBe listOf("2", "1")
            cut.events.first().map { it.first().eventId.full } shouldBe listOf("3", "2", "1")
            cut.lastLoadedEventIdBefore.first()?.full shouldBe "3"
            cut.lastLoadedEventIdAfter.first()?.full shouldBe "1"
            cut.isLoadingAfter.first() shouldBe false
        }
    }
})