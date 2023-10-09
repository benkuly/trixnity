package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

fun plainEvent(
    i: Long = 24,
    roomId: RoomId = RoomId("room", "server")
): MessageEvent<RoomMessageEventContent.TextMessageEventContent> {
    return MessageEvent(
        RoomMessageEventContent.TextMessageEventContent("message $i"),
        EventId("\$event$i"),
        UserId("sender", "server"),
        roomId,
        i
    )
}

fun timeline(block: TimelineBuilder.() -> Unit) =
    TimelineBuilder().apply(block).timeline

class TimelineBuilder {
    val timeline = mutableListOf<TimelineEvent>()
    fun fragment(block: TimelineFragmentBuilder.() -> Unit) {
        timeline += TimelineFragmentBuilder().apply(block).timeline
    }
}

class TimelineFragmentBuilder {
    val timeline = mutableListOf<TimelineEvent>()
    private var currentGap: String? = null
    operator fun RoomEvent<*>.unaryPlus() {
        val previousTimelineEvent = timeline.removeLastOrNull()
        if (previousTimelineEvent != null)
            timeline += previousTimelineEvent.copy(
                nextEventId = this.id
            )
        timeline += TimelineEvent(
            event = this,
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
            val gap = previousTimelineEvent.gap
            timeline += previousTimelineEvent.copy(
                gap = when {
                    gap == null -> GapAfter(batch)
                    gap.hasGapBoth || gap.hasGapAfter -> previousTimelineEvent.gap
                    gap.hasGapBefore -> GapBoth(requireNotNull(gap.batchBefore), batch)
                    else -> GapAfter(batch)
                }
            )
        }
    }
}