package net.folivo.trixnity.client.room

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.replace
import kotlin.time.Duration.Companion.minutes

sealed interface TimelineEventAggregation {
    /**
     * @param replacedBy Event, that replaces the event. Null, if there is no replacement.
     * @param history List of all events replacing the event. Sorted ascending by [MessageEvent.originTimestamp].
     */
    data class Replace(val replacedBy: EventId?, val history: List<EventId>) : TimelineEventAggregation

    data class Reaction(val reactions: Map<String, Set<UserId>>) : TimelineEventAggregation
}

@OptIn(ExperimentalCoroutinesApi::class)
fun RoomService.getTimelineEventReplaceAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Replace> =
    getTimelineEventRelations(roomId, eventId, RelationType.Replace)
        .map { it?.keys }
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

fun RoomService.getTimelineEventReactionAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Reaction> =
    getTimelineEventRelations(roomId, eventId, RelationType.Annotation)
        .map { it?.keys.orEmpty() }
        .map { relations ->
            val reactions = coroutineScope {
                // TODO fetching every single TimelineEvent from store is very inefficient and does not scale
                relations.map {
                    async {
                        withTimeoutOrNull(1.minutes) { getTimelineEvent(roomId, it).first() }
                    }
                }.awaitAll()
            }.filterNotNull()
                .mapNotNull {
                    val relatesTo = it.relatesTo
                    if (relatesTo is RelatesTo.Annotation) {
                        val key = relatesTo.key
                        if (key != null) key to it.sender
                        else null
                    } else null
                }
                .distinct()
                .groupBy { it.first }
                .mapValues { entry -> entry.value.map { it.second }.toSet() }
            TimelineEventAggregation.Reaction(reactions)
        }