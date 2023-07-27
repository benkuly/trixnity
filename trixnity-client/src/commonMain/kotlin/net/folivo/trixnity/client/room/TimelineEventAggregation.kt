package net.folivo.trixnity.client.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.replace

sealed interface TimelineEventAggregation {
    /**
     * @param replacedBy Event, that replaces the event. Null, if there is no replacement.
     * @param history List of all events replacing the event. Sorted ascending by [MessageEvent.originTimestamp].
     */
    data class Replace(val replacedBy: EventId?, val history: List<EventId>) : TimelineEventAggregation
}

@OptIn(ExperimentalCoroutinesApi::class)
fun RoomService.getTimelineEventReplaceAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Replace> =
    getTimelineEventRelations(roomId, eventId, RelationType.Replace)
        .map { relations -> relations?.keys }
        .transformLatest { relations ->
            val serverAggregation = getTimelineEvent(roomId, eventId) { allowReplaceContent = false }
                .first()?.event?.unsigned?.relations?.replace?.eventId
            emit(
                when {
                    relations == null -> setOfNotNull(serverAggregation)
                    serverAggregation == null -> relations
                    else -> relations + serverAggregation
                }
            )
        }
        .map { relations ->
            val timelineEvent = getTimelineEvent(roomId, eventId) { allowReplaceContent = false }.first()
            val history = relations.mapNotNull { getTimelineEvent(roomId, it) { allowReplaceContent = false }.first() }
                .filter { it.event.sender == timelineEvent?.sender }
                .sortedBy { it.event.originTimestamp }
                .map { it.eventId }
            TimelineEventAggregation.Replace(history.lastOrNull(), history)
        }