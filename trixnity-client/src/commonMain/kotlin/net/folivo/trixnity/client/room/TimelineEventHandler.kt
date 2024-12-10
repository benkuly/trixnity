package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.subscribeEvent
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.utils.KeyedMutex

private val log = KotlinLogging.logger {}

interface TimelineEventHandler {
    /**
     * Unsafe means, that it may throw exceptions
     */
    suspend fun unsafeFillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    ): Result<Unit>
}

class TimelineEventHandlerImpl(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val tm: TransactionManager,
) : EventHandler, TimelineEventHandler {
    companion object {
        const val LAZY_LOAD_MEMBERS_FILTER = """{"lazy_load_members":true}"""
    }

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEvent(subscriber = ::redactTimelineEvent).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.STORE_TIMELINE_EVENTS, ::handleSyncResponse).unsubscribeOnCompletion(scope)
    }

    private val timelineMutex = KeyedMutex<RoomId>()

    internal suspend fun handleSyncResponse(syncEvents: SyncEvents) {
        val syncResponse = syncEvents.syncResponse
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            room.value.timeline?.also {
                addEventsToTimelineAtEnd(
                    roomId = roomId,
                    newEvents = it.events,
                    previousBatch = it.previousBatch,
                    nextBatch = syncResponse.nextBatch,
                    hasGapBefore = it.limited == true
                )
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            room.value.timeline?.also {
                addEventsToTimelineAtEnd(
                    roomId = room.key,
                    newEvents = it.events,
                    previousBatch = it.previousBatch,
                    nextBatch = syncResponse.nextBatch,
                    hasGapBefore = it.limited == true
                )
            }
        }
    }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        newEvents: List<RoomEvent<*>>?,
        previousBatch: String?,
        nextBatch: String,
        hasGapBefore: Boolean,
    ) {
        timelineMutex.withLock(roomId) {
            tm.transaction {
                val events = roomTimelineStore.filterDuplicateEvents(newEvents)
                if (!events.isNullOrEmpty()) {
                    log.debug { "add events to timeline at end of $roomId" }
                    val lastEventId = roomStore.get(roomId).first()?.lastEventId
                    suspend fun useDecryptedOutboxMessagesForOwnTimelineEvents(timelineEvents: List<TimelineEvent>) =
                        timelineEvents.map {
                            if (it.event.isEncrypted) {
                                it.event.unsigned?.transactionId?.let { transactionId ->
                                    roomOutboxMessageStore.get(roomId, transactionId).first()
                                        ?.let { roomOutboxMessage ->
                                            it.copy(content = Result.success(roomOutboxMessage.content))
                                        }
                                } ?: it
                            } else it
                        }
                    roomTimelineStore.addEventsToTimeline(
                        startEvent = TimelineEvent(
                            event = events.first(),
                            previousEventId = null,
                            nextEventId = null,
                            gap = null
                        ),
                        roomId = roomId,
                        previousToken = previousBatch,
                        previousHasGap = hasGapBefore,
                        previousEvent = lastEventId,
                        previousEventChunk = null,
                        nextToken = nextBatch,
                        nextHasGap = true,
                        nextEvent = null,
                        nextEventChunk = events.drop(1),
                        processTimelineEventsBeforeSave = { list ->
                            useDecryptedOutboxMessagesForOwnTimelineEvents(list)
                        }
                    )
                    events.alsoAddRelationFromTimelineEvents()
                }
                events?.lastOrNull()?.also { event -> setLastEventId(event) }
            }
        }
    }

    override suspend fun unsafeFillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long
    ): Result<Unit> = timelineMutex.withLock(roomId) {
        kotlin.runCatching {
            val isLastEventId = roomStore.get(roomId).first()?.lastEventId == startEventId

            val startEvent = roomTimelineStore.get(startEventId, roomId).first() ?: return@runCatching
            val previousToken: String?
            val previousHasGap: Boolean
            val previousEvent: EventId?
            val previousEventChunk: List<RoomEvent<*>>?
            val nextToken: String?
            val nextHasGap: Boolean
            val nextEvent: EventId?
            val nextEventChunk: List<RoomEvent<*>>?

            var insertNewEvents = false

            val startGap = startEvent.gap
            val startGapBatchBefore = startGap?.batchBefore
            val startGapBatchAfter = startGap?.batchAfter

            val possiblyPreviousEvent = roomTimelineStore.getPrevious(startEvent)
            if (startGapBatchBefore != null) {
                insertNewEvents = true
                log.debug { "fetch missing events before $startEventId" }
                val destinationBatch = possiblyPreviousEvent?.gap?.batchAfter
                val response = api.room.getEvents(
                    roomId = roomId,
                    from = startGapBatchBefore,
                    to = destinationBatch,
                    dir = GetEvents.Direction.BACKWARDS,
                    limit = limit,
                    filter = LAZY_LOAD_MEMBERS_FILTER
                ).getOrThrow()
                previousToken = response.end?.takeIf { it != response.start } // detects start of timeline
                previousEvent = possiblyPreviousEvent?.eventId
                previousEventChunk = roomTimelineStore.filterDuplicateEvents(response.chunk)
                previousHasGap = response.end != null &&
                        response.end != destinationBatch &&
                        response.chunk?.none { it.id == previousEvent } == true
            } else {
                previousToken = null
                previousEvent = possiblyPreviousEvent?.eventId
                previousEventChunk = null
                previousHasGap = false
            }

            val possiblyNextEvent = roomTimelineStore.getNext(startEvent)?.first()
            if (startGapBatchAfter != null && !isLastEventId) {
                insertNewEvents = true
                log.debug { "fetch missing events after $startEventId" }
                val destinationBatch = possiblyNextEvent?.gap?.batchBefore
                val response = api.room.getEvents(
                    roomId = roomId,
                    from = startGapBatchAfter,
                    to = destinationBatch,
                    dir = GetEvents.Direction.FORWARDS,
                    limit = limit,
                    filter = LAZY_LOAD_MEMBERS_FILTER
                ).getOrThrow()
                nextToken = response.end
                nextEvent = possiblyNextEvent?.eventId
                nextEventChunk = roomTimelineStore.filterDuplicateEvents(response.chunk)
                nextHasGap = response.end != null &&
                        response.end != destinationBatch &&
                        response.chunk?.none { it.id == nextEvent } == true
            } else {
                nextToken = startGapBatchAfter
                nextEvent = possiblyNextEvent?.eventId
                nextEventChunk = null
                nextHasGap = isLastEventId
            }

            if (insertNewEvents)
                tm.transaction {
                    roomTimelineStore.addEventsToTimeline(
                        startEvent = startEvent,
                        roomId = roomId,
                        previousToken = previousToken,
                        previousHasGap = previousHasGap,
                        previousEvent = previousEvent,
                        previousEventChunk = previousEventChunk,
                        nextToken = nextToken,
                        nextHasGap = nextHasGap,
                        nextEvent = nextEvent,
                        nextEventChunk = nextEventChunk,
                        processTimelineEventsBeforeSave = { list ->
                            list.forEach {
                                val event = it.event
                                val content = event.content
                                if (content is RedactionEventContent) {
                                    @Suppress("UNCHECKED_CAST")
                                    redactTimelineEvent(event as RoomEvent<RedactionEventContent>)
                                }
                            }
                            list
                        },
                    )
                    previousEventChunk?.alsoAddRelationFromTimelineEvents()
                    nextEventChunk?.alsoAddRelationFromTimelineEvents()
                }
        }
    }

    internal suspend fun setLastEventId(event: ClientEvent<*>) {
        if (event is RoomEvent) {
            roomStore.update(event.roomId) { oldRoom ->
                oldRoom?.copy(lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventId = event.id)
            }
        }
    }

    internal suspend fun redactTimelineEvent(redactionEvent: RoomEvent<RedactionEventContent>) {
        if (redactionEvent is MessageEvent) {
            val roomId = redactionEvent.roomId
            log.debug { "redact event with id ${redactionEvent.content.redacts} in room $roomId" }
            roomTimelineStore.update(redactionEvent.content.redacts, roomId) { oldTimelineEvent ->
                if (oldTimelineEvent != null) {
                    when (val oldEvent = oldTimelineEvent.event) {
                        is MessageEvent -> {
                            redactRelation(oldEvent)
                            val eventType =
                                api.eventContentSerializerMappings.message
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            val newContent = RedactedEventContent(eventType)
                            oldTimelineEvent.copy(
                                event = MessageEvent(
                                    newContent,
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedRoomEventData.UnsignedMessageEventData(
                                        redactedBecause = redactionEvent,
                                        transactionId = oldEvent.unsigned?.transactionId,
                                    )
                                ),
                                content = Result.success(newContent),
                            )
                        }

                        is RoomEvent.StateEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.state
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            val newContent = RedactedEventContent(eventType)
                            oldTimelineEvent.copy(
                                event = RoomEvent.StateEvent(
                                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.10/rooms/v9/#redactions
                                    newContent,
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedRoomEventData.UnsignedStateEventData(
                                        redactedBecause = redactionEvent,
                                        transactionId = oldEvent.unsigned?.transactionId,
                                    ),
                                    oldEvent.stateKey,
                                ),
                                content = Result.success(newContent),
                            )
                        }
                    }
                } else {
                    log.debug { "nothing to redact as event with id ${redactionEvent.content.redacts} in room $roomId does not exist locally" }
                    null
                }
            }
        }
    }

    private suspend fun List<RoomEvent<*>>.alsoAddRelationFromTimelineEvents() =
        asFlow().filterIsInstance<MessageEvent<MessageEventContent>>().collect(::addRelation)

    internal suspend fun addRelation(event: MessageEvent<MessageEventContent>) {
        val relatesTo = event.content.relatesTo
        if (relatesTo != null) {
            log.debug { "add relation to ${relatesTo.eventId}" }
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    roomId = event.roomId,
                    eventId = event.id,
                    relationType = relatesTo.relationType,
                    relatedEventId = relatesTo.eventId,
                )
            )
        }
    }

    internal suspend fun redactRelation(redactedEvent: MessageEvent<*>) {
        val relatesTo = redactedEvent.content.relatesTo
        if (relatesTo != null) {
            log.debug { "delete relation from ${redactedEvent.id}" }
            roomTimelineStore.deleteRelation(
                TimelineEventRelation(
                    roomId = redactedEvent.roomId,
                    eventId = redactedEvent.id,
                    relationType = relatesTo.relationType,
                    relatedEventId = relatesTo.eventId,
                )
            )
        }
    }

    private suspend fun RoomTimelineStore.filterDuplicateEvents(
        events: List<RoomEvent<*>>?,
    ) =
        events?.distinctBy { it.id }
            ?.filter { get(it.id, it.roomId).first() == null }

    private suspend fun RoomTimelineStore.addEventsToTimeline(
        startEvent: TimelineEvent,
        roomId: RoomId,
        previousToken: String?,
        previousHasGap: Boolean,
        previousEvent: EventId?,
        previousEventChunk: List<RoomEvent<*>>?,
        nextToken: String?,
        nextHasGap: Boolean,
        nextEvent: EventId?,
        nextEventChunk: List<RoomEvent<*>>?,
        processTimelineEventsBeforeSave: suspend (List<TimelineEvent>) -> List<TimelineEvent>
    ) {
        log.trace {
            "addEventsToTimeline with parameters:\n" +
                    "startEvent=${startEvent.eventId.full}\n" +
                    "previousToken=$previousToken, previousHasGap=$previousHasGap, previousEvent=${previousEvent?.full}, previousEventChunk=${previousEventChunk?.map { it.id.full }}\n" +
                    "nextToken=$nextToken, nextHasGap=$nextHasGap, nextEvent=${nextEvent?.full}, nextEventChunk=${nextEventChunk?.map { it.id.full }}"
        }

        val updatedPreviousEvent =
            if (previousEvent != null)
                get(previousEvent, roomId).first()?.let { oldPreviousEvent ->
                    val oldGap = oldPreviousEvent.gap
                    oldPreviousEvent.copy(
                        nextEventId = previousEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                        gap = if (previousHasGap) oldGap else oldGap?.removeGapAfter(),
                    )
                }
            else null

        val updatedNextEvent =
            if (nextEvent != null)
                get(nextEvent, roomId).first()?.let { oldNextEvent ->
                    val oldGap = oldNextEvent.gap
                    oldNextEvent.copy(
                        previousEventId = nextEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                        gap = if (nextHasGap) oldGap else oldGap?.removeGapBefore()
                    )
                }
            else null

        val updatedStartEvent =
            get(startEvent.eventId, roomId).first().let { oldStartEvent ->
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
                )
            }

        val newPreviousEvents =
            if (!previousEventChunk.isNullOrEmpty()) {
                log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
                previousEventChunk.mapIndexed { index, event ->
                    when (index) {
                        previousEventChunk.lastIndex -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEvent,
                                nextEventId = if (index == 0) startEvent.eventId
                                else previousEventChunk.getOrNull(index - 1)?.id,
                                gap = if (previousHasGap) previousToken?.let { TimelineEvent.Gap.GapBefore(it) } else null
                            )
                        }

                        0 -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEventChunk.getOrNull(1)?.id,
                                nextEventId = startEvent.eventId,
                                gap = null
                            )
                        }

                        else -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEventChunk.getOrNull(index + 1)?.id,
                                nextEventId = previousEventChunk.getOrNull(index - 1)?.id,
                                gap = null
                            )
                        }
                    }
                }
            } else emptyList()

        val newNextEvents =
            if (!nextEventChunk.isNullOrEmpty()) {
                log.debug { "add events to timeline of $roomId after ${startEvent.eventId}" }
                nextEventChunk.mapIndexed { index, event ->
                    when (index) {
                        nextEventChunk.lastIndex -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = if (index == 0) startEvent.eventId
                                else nextEventChunk.getOrNull(index - 1)?.id,
                                nextEventId = nextEvent,
                                gap = if (nextHasGap) nextToken?.let { TimelineEvent.Gap.GapAfter(it) } else null,
                            )
                        }

                        0 -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = startEvent.eventId,
                                nextEventId = nextEventChunk.getOrNull(1)?.id,
                                gap = null
                            )
                        }

                        else -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = nextEventChunk.getOrNull(index - 1)?.id,
                                nextEventId = nextEventChunk.getOrNull(index + 1)?.id,
                                gap = null
                            )
                        }
                    }
                }
            } else emptyList()

        // TODO this is a workaround to prevent loops from sync cancellations (there is no general rollback of the cache yet)
        withContext(NonCancellable) {
            // saving (saveAll) must be split up, because processTimelineEventsBeforeSave may redact events
            addAll(
                processTimelineEventsBeforeSave(
                    listOfNotNull(
                        updatedPreviousEvent,
                        updatedNextEvent,
                        updatedStartEvent,
                    )
                )
            )
            addAll(processTimelineEventsBeforeSave(newPreviousEvents))
            addAll(processTimelineEventsBeforeSave(newNextEvents))
        }
    }
}
