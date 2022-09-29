package net.folivo.trixnity.client.room

import mu.KotlinLogging
import net.folivo.trixnity.client.store.RoomTimelineStore
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

private val log = KotlinLogging.logger {}

internal suspend fun RoomTimelineStore.filterDuplicateEvents(events: List<Event.RoomEvent<*>>?) =
    events?.distinctBy { it.id }
        ?.filter { get(it.id, it.roomId, withTransaction = false) == null }

internal suspend fun RoomTimelineStore.addEventsToTimeline(
    startEvent: TimelineEvent,
    roomId: RoomId,
    previousToken: String?,
    previousHasGap: Boolean,
    previousEvent: EventId?,
    previousEventChunk: List<Event.RoomEvent<*>>?,
    nextToken: String?,
    nextHasGap: Boolean,
    nextEvent: EventId?,
    nextEventChunk: List<Event.RoomEvent<*>>?,
    processTimelineEventsBeforeSave: suspend (List<TimelineEvent>) -> List<TimelineEvent> = { it }
) {
    log.trace {
        "addEventsToTimeline with parameters:\n" +
                "startEvent=${startEvent.eventId.full}\n" +
                "previousToken=$previousToken, previousHasGap=$previousHasGap, previousEvent=${previousEvent?.full}, previousEventChunk=${previousEventChunk?.map { it.id.full }}\n" +
                "nextToken=$nextToken, nextHasGap=$nextHasGap, nextEvent=${nextEvent?.full}, nextEventChunk=${nextEventChunk?.map { it.id.full }}"
    }

    if (previousEvent != null)
        update(previousEvent, roomId, withTransaction = false) { oldPreviousEvent ->
            val oldGap = oldPreviousEvent?.gap
            oldPreviousEvent?.copy(
                nextEventId = previousEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                gap = if (previousHasGap) oldGap else oldGap?.removeGapAfter(),
            )?.let { processTimelineEventsBeforeSave(listOf(it)).first() }
        }
    if (nextEvent != null)
        update(nextEvent, roomId, withTransaction = false) { oldNextEvent ->
            val oldGap = oldNextEvent?.gap
            oldNextEvent?.copy(
                previousEventId = nextEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                gap = if (nextHasGap) oldGap else oldGap?.removeGapBefore()
            )?.let { processTimelineEventsBeforeSave(listOf(it)).first() }
        }
    update(startEvent.eventId, roomId, withTransaction = false) { oldStartEvent ->
        val hasGapBefore = previousEventChunk.isNullOrEmpty() && previousHasGap
        val hasGapAfter = nextEventChunk.isNullOrEmpty() && nextHasGap
        (oldStartEvent ?: startEvent).copy(
            previousEventId = previousEventChunk?.firstOrNull()?.id ?: previousEvent,
            nextEventId = nextEventChunk?.firstOrNull()?.id ?: nextEvent,
            gap = when {
                hasGapBefore && hasGapAfter && previousToken != null && nextToken != null
                -> TimelineEvent.Gap.GapBoth(previousToken, nextToken)

                hasGapBefore && previousToken != null -> TimelineEvent.Gap.GapBefore(previousToken)
                hasGapAfter && nextToken != null -> TimelineEvent.Gap.GapAfter(nextToken)
                else -> null
            }
        ).let { processTimelineEventsBeforeSave(listOf(it)).first() }
    }

    if (!previousEventChunk.isNullOrEmpty()) {
        log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
        val timelineEvents = previousEventChunk.mapIndexed { index, event ->
            when (index) {
                previousEventChunk.lastIndex -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = previousEvent,
                        nextEventId = if (index == 0) startEvent.eventId
                        else previousEventChunk.getOrNull(index - 1)?.id,
                        gap = if (previousHasGap) previousToken?.let { TimelineEvent.Gap.GapBefore(it) } else null
                    )
                }

                0 -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = previousEventChunk.getOrNull(1)?.id,
                        nextEventId = startEvent.eventId,
                        gap = null
                    )
                }

                else -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = previousEventChunk.getOrNull(index + 1)?.id,
                        nextEventId = previousEventChunk.getOrNull(index - 1)?.id,
                        gap = null
                    )
                }
            }
        }
        addAll(processTimelineEventsBeforeSave(timelineEvents), withTransaction = false)
    }

    if (!nextEventChunk.isNullOrEmpty()) {
        log.debug { "add events to timeline of $roomId after ${startEvent.eventId}" }
        val timelineEvents = nextEventChunk.mapIndexed { index, event ->
            when (index) {
                nextEventChunk.lastIndex -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = if (index == 0) startEvent.eventId
                        else nextEventChunk.getOrNull(index - 1)?.id,
                        nextEventId = nextEvent,
                        gap = if (nextHasGap) nextToken?.let { TimelineEvent.Gap.GapAfter(it) } else null,
                    )
                }

                0 -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = startEvent.eventId,
                        nextEventId = nextEventChunk.getOrNull(1)?.id,
                        gap = null
                    )
                }

                else -> {
                    TimelineEvent(
                        event = event,
                        roomId = roomId,
                        eventId = event.id,
                        previousEventId = nextEventChunk.getOrNull(index - 1)?.id,
                        nextEventId = nextEventChunk.getOrNull(index + 1)?.id,
                        gap = null
                    )
                }
            }
        }
        addAll(processTimelineEventsBeforeSave(timelineEvents), withTransaction = false)
    }
}