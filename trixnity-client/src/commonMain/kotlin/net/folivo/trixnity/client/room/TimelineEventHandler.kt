package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.users.Filters
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
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.utils.KeyedMutex

private val log = KotlinLogging.logger("net.folivo.trixnity.client.room.TimelineEventHandler")

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
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
    private val config: MatrixClientConfiguration,
    private val tm: TransactionManager,
) : EventHandler, TimelineEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.STORE_TIMELINE_EVENTS, ::handleSyncResponse).unsubscribeOnCompletion(scope)
    }

    private val timelineFilter by lazy {
        val baseFilter = config.syncFilter
        val filter = (baseFilter.room?.timeline ?: Filters.RoomFilter.RoomEventFilter()).copy(
            types = (mappings.message + mappings.state).map { it.type }.toSet(),
        )
        json.encodeToString(filter)
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
            tm.writeTransaction {
                val events =
                    roomTimelineStore.filterDuplicateEvents(newEvents)
                        ?.handleRedactions()
                if (!events.isNullOrEmpty()) {
                    log.debug { "add events to timeline at end of $roomId" }
                    val lastEventId = roomStore.get(roomId).first()?.lastEventId
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
                    filter = timelineFilter
                ).getOrThrow()
                previousToken = response.end?.takeIf { it != response.start } // detects start of timeline
                previousEvent = possiblyPreviousEvent?.eventId
                previousEventChunk = roomTimelineStore
                    .filterDuplicateEvents(response.chunk)
                    ?.handleRedactions()
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
                    filter = timelineFilter
                ).getOrThrow()
                nextToken = response.end
                nextEvent = possiblyNextEvent?.eventId
                nextEventChunk = roomTimelineStore
                    .filterDuplicateEvents(response.chunk)
                    ?.handleRedactions()
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
                tm.writeTransaction {
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
            }
        }
    }

    private suspend fun RoomEvent<*>.redact(because: MessageEvent<RedactionEventContent>): RoomEvent<RedactedEventContent> =
        when (this) {
            is MessageEvent -> {
                redactRelation(this)
                val redactedContent = content as? RedactedEventContent
                    ?: RedactedEventContent(
                        api.eventContentSerializerMappings.message
                            .find { it.kClass.isInstance(content) }?.type
                            ?: "UNKNOWN"
                    )
                MessageEvent(
                    redactedContent,
                    id,
                    sender,
                    roomId,
                    originTimestamp,
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        redactedBecause = because,
                        transactionId = unsigned?.transactionId,
                    )
                )
            }

            is RoomEvent.StateEvent -> {
                // TODO should update state to last known (maybe not needed with sync v3)
                val redactedContent = content as? RedactedEventContent
                    ?: RedactedEventContent(
                        api.eventContentSerializerMappings.state
                            .find { it.kClass.isInstance(content) }?.type
                            ?: "UNKNOWN"
                    )
                RoomEvent.StateEvent(
                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.10/rooms/v9/#redactions
                    redactedContent,
                    id,
                    sender,
                    roomId,
                    originTimestamp,
                    UnsignedRoomEventData.UnsignedStateEventData(
                        redactedBecause = because,
                        transactionId = unsigned?.transactionId,
                    ),
                    stateKey,
                )
            }
        }

    internal suspend fun List<RoomEvent<*>>.handleRedactions(): List<RoomEvent<*>> {
        val redactionEvents =
            filter { it.content is RedactionEventContent }
                .filterIsInstance<MessageEvent<RedactionEventContent>>()
                .associateBy { it.content.redacts }
                .toMutableMap()

        val eventsWithRedactedEvents = map { event ->
            val redactedBecause = redactionEvents[event.id]
            if (redactedBecause != null && redactedBecause != event) {
                log.debug { "redact new event with id ${redactedBecause.content.redacts} in room ${redactedBecause.roomId}" }
                redactionEvents.remove(event.id)
                event.redact(redactedBecause)
            } else event
        }
        redactionEvents
            .forEach { (_, redactionEvent) ->
                val roomId = redactionEvent.roomId
                roomTimelineStore.update(redactionEvent.content.redacts, roomId) { oldTimelineEvent ->
                    if (oldTimelineEvent != null) {
                        log.debug { "redact existing event with id ${redactionEvent.content.redacts} in room $roomId" }
                        val newEvent = oldTimelineEvent.event.redact(redactionEvent)
                        oldTimelineEvent.copy(
                            event = newEvent,
                            content = Result.success(newEvent.content),
                        )
                    } else {
                        log.trace { "redact nothing because event with id ${redactionEvent.content.redacts} in room $roomId does not exist locally" }
                        null
                    }
                }
            }

        return eventsWithRedactedEvents
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
        roomTimelineStore.deleteRelations(redactedEvent.id, redactedEvent.roomId, RelationType.Replace)
    }

    private suspend fun RoomTimelineStore.filterDuplicateEvents(
        events: List<RoomEvent<*>>?,
    ) =
        events?.distinctBy { it.id }
            ?.filter { get(it.id, it.roomId).first() == null }

    internal suspend fun RoomTimelineStore.addEventsToTimeline(
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

        addAll(
            listOfNotNull(
                updatedPreviousEvent,
                updatedNextEvent,
                updatedStartEvent,
            ) + newPreviousEvents + newNextEvents
        )
    }
}