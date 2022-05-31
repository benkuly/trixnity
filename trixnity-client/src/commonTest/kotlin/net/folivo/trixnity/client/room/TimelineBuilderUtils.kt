package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

fun timeline(roomId: RoomId = RoomId("room", "server"), block: TimelineBuilder.() -> Unit) =
    TimelineBuilder(roomId).apply(block).timeline

class TimelineBuilder(private val roomId: RoomId) {
    val timeline = mutableListOf<TimelineEvent>()
    fun fragment(block: TimelineFragmentBuilder.() -> Unit) {
        timeline += TimelineFragmentBuilder(roomId).apply(block).timeline
    }
}

class TimelineFragmentBuilder(private val roomId: RoomId) {
    val timeline = mutableListOf<TimelineEvent>()
    private var currentGap: String? = null
    fun event(i: Long) {
        val eventId = EventId("\$event$i")
        val previousTimelineEvent = timeline.removeLastOrNull()
        if (previousTimelineEvent != null)
            timeline += previousTimelineEvent.copy(
                nextEventId = eventId
            )
        timeline += TimelineEvent(
            event = Event.MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message $i"),
                eventId,
                UserId("sender", "server"),
                roomId,
                i
            ),
            previousEventId = timeline.lastOrNull()?.eventId,
            nextEventId = null,
            gap = currentGap?.let { GapBefore(it) }
        )
        currentGap = null
    }

    fun gap(batch: String) {
        currentGap = batch
        val previousTimelineEvent = timeline.removeLastOrNull()
        if (previousTimelineEvent != null) {
            timeline += previousTimelineEvent.copy(
                gap = when (previousTimelineEvent.gap) {
                    is GapBefore -> GapBoth(batch)
                    is GapAfter -> previousTimelineEvent.gap
                    is GapBoth -> previousTimelineEvent.gap
                    null -> GapAfter(batch)
                }
            )
        }
    }
}