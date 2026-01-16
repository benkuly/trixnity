package de.connect2x.trixnity.client.room

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.replace
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

sealed interface TimelineEventAggregation {
    /**
     * @param replacedBy Event, that replaces the event. Null, if there is no replacement.
     * @param history List of all events replacing the event. Sorted ascending by [MessageEvent.originTimestamp].
     */
    data class Replace(val replacedBy: EventId?, val history: List<EventId>) : TimelineEventAggregation

    data class Reaction(val reactions: Map<String, Set<TimelineEvent>>) : TimelineEventAggregation
}

@OptIn(ExperimentalCoroutinesApi::class)
fun RoomService.getTimelineEventReplaceAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Replace> =
    getTimelineEventRelations(roomId, eventId, RelationType.Replace)
        .flatMapLatest { replaceMap ->
            if (replaceMap.isNullOrEmpty()) flowOf(emptySet())
            else combine(replaceMap.values) {
                it.mapNotNull { replace -> replace?.eventId }.toSet()
            }
        }
        .map { relations ->
            val serverAggregation = getTimelineEvent(roomId, eventId) {
                allowReplaceContent = false
                decryptionTimeout = ZERO
            }.first()?.event?.unsigned?.relations?.replace?.eventId
            if (serverAggregation != null) relations + serverAggregation
            else relations
        }
        .map { relations ->
            val timelineEvent = getTimelineEvent(roomId, eventId) {
                allowReplaceContent = false
                decryptionTimeout = ZERO
            }.first()
            val history = relations.mapNotNull {
                getTimelineEvent(roomId, it) {
                    allowReplaceContent = false
                    decryptionTimeout = ZERO
                }.first()
            }.filter { it.event.sender == timelineEvent?.sender }
                .sortedWith(
                    compareBy<TimelineEvent> { it.originTimestamp }
                        .thenBy { it.eventId.full }
                ).map { it.eventId }
            TimelineEventAggregation.Replace(history.lastOrNull(), history)
        }

@OptIn(ExperimentalCoroutinesApi::class)
fun RoomService.getTimelineEventReactionAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Reaction> {
    val result = getTimelineEventRelations(roomId, eventId, RelationType.Annotation)
        .flatMapLatest { reactionMap ->
            if (reactionMap.isNullOrEmpty()) flowOf(emptyList())
            else combine(reactionMap.values) {
                it.mapNotNull { reaction -> reaction?.eventId }
            }
        }
        .map { relations ->
            coroutineScope {
                // TODO fetching every single TimelineEvent from store is very inefficient and does not scale
                relations.map {
                    async {
                        withTimeoutOrNull(1.minutes) {
                            getTimelineEvent(roomId, it).first()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }.map { reactions ->
            reactions.mapNotNull {
                val relatesTo = it.relatesTo
                if (relatesTo is RelatesTo.Annotation) {
                    val key = relatesTo.key
                    if (key != null) key to it
                    else null
                } else null
            }.groupBy { (reaction, _) ->
                reaction
            }.mapValues { (_, events) ->
                events.map { (_, event) -> event }
                    .groupBy { it.sender }
                    .map { (_, senderEvents) -> senderEvents.maxBy { it.originTimestamp } }
                    .toSet()
            }
        }.map { reactions ->
            TimelineEventAggregation.Reaction(reactions)
        }
    return result
}
