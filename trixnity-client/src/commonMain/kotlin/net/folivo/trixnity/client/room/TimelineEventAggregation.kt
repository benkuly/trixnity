package net.folivo.trixnity.client.room

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.originTimestamp
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.replace
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
        .map { it?.keys }
        .transformLatest { relations ->
            val serverAggregation = getTimelineEvent(roomId, eventId) {
                allowReplaceContent = false
                decryptionTimeout = ZERO
            }.first()?.event?.unsigned?.relations?.replace?.eventId
            emit(
                when {
                    relations == null -> setOfNotNull(serverAggregation)
                    serverAggregation == null -> relations
                    else -> relations + serverAggregation
                }
            )
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
                .sortedBy { it.event.originTimestamp }
                .map { it.eventId }
            TimelineEventAggregation.Replace(history.lastOrNull(), history)
        }

fun RoomService.getTimelineEventReactionAggregation(
    roomId: RoomId,
    eventId: EventId,
): Flow<TimelineEventAggregation.Reaction> {
    val result = getTimelineEventRelations(roomId, eventId, RelationType.Annotation)
        .flatMapLatest { reactionMap ->
            combine(reactionMap?.values.orEmpty()) {
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
