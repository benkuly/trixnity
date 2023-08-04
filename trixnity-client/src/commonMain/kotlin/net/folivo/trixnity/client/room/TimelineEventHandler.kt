package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

interface TimelineEventHandler {
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
    private val timelineMutex: TimelineMutex,
    private val tm: RepositoryTransactionManager,
) : EventHandler, TimelineEventHandler {
    companion object {
        const val LAZY_LOAD_MEMBERS_FILTER = """{"lazy_load_members":true}"""
    }

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::redactTimelineEvent)
        api.sync.subscribeFirstInSyncProcessing(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::redactTimelineEvent)
            api.sync.unsubscribeFirstInSyncProcessing(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) = tm.writeTransaction {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            room.value.timeline?.also {
                timelineMutex.withLock(roomId) {
                    addEventsToTimelineAtEnd(
                        roomId = roomId,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.also { event -> setLastEventId(event) }
                }
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            room.value.timeline?.also {
                timelineMutex.withLock(room.key) {
                    addEventsToTimelineAtEnd(
                        roomId = room.key,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.let { event -> setLastEventId(event) }
                }
            }
        }
    }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        newEvents: List<Event.RoomEvent<*>>?,
        previousBatch: String?,
        nextBatch: String,
        hasGapBefore: Boolean
    ) {
        val events = roomTimelineStore.filterDuplicateEvents(newEvents)
        if (!events.isNullOrEmpty()) {
            log.debug { "add events to timeline at end of $roomId" }
            val lastEventId = roomStore.get(roomId).first()?.lastEventId
            suspend fun useDecryptedOutboxMessagesForOwnTimelineEvents(timelineEvents: List<TimelineEvent>) =
                timelineEvents.map {
                    if (it.event.isEncrypted) {
                        it.event.unsigned?.transactionId?.let { transactionId ->
                            roomOutboxMessageStore.get(transactionId)?.let { roomOutboxMessage ->
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
                    list.alsoAddRelationFromTimelineEvents()
                    useDecryptedOutboxMessagesForOwnTimelineEvents(list)
                }
            )
        }
    }

    override suspend fun unsafeFillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long
    ): Result<Unit> = timelineMutex.withLock(roomId) {
        kotlin.runCatching {
            tm.writeTransaction {
                val isLastEventId = roomStore.get(roomId).first()?.lastEventId == startEventId

                val startEvent = roomTimelineStore.get(startEventId, roomId).first() ?: return@writeTransaction
                val previousToken: String?
                val previousHasGap: Boolean
                val previousEvent: EventId?
                val previousEventChunk: List<Event.RoomEvent<*>>?
                val nextToken: String?
                val nextHasGap: Boolean
                val nextEvent: EventId?
                val nextEventChunk: List<Event.RoomEvent<*>>?

                var insertNewEvents = false

                val startGap = startEvent.gap
                val startGapBatchBefore = startGap?.batchBefore
                val startGapBatchAfter = startGap?.batchAfter

                val possiblyPreviousEvent = roomTimelineStore.getPrevious(startEvent)
                if (startGapBatchBefore != null) {
                    insertNewEvents = true
                    log.debug { "fetch missing events before $startEventId" }
                    val destinationBatch = possiblyPreviousEvent?.gap?.batchAfter
                    val response = api.rooms.getEvents(
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
                    val response = api.rooms.getEvents(
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
                            list.alsoAddRelationFromTimelineEvents()
                            list.forEach {
                                val event = it.event
                                val content = event.content
                                if (content is RedactionEventContent) {
                                    @Suppress("UNCHECKED_CAST")
                                    redactTimelineEvent(event as Event<RedactionEventContent>)
                                }
                            }
                            list
                        },
                    )
            }
        }
    }

    internal suspend fun setLastEventId(event: Event<*>) {
        if (event is Event.RoomEvent) {
            roomStore.update(event.roomId) { oldRoom ->
                oldRoom?.copy(lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventId = event.id)
            }
        }
    }

    internal suspend fun redactTimelineEvent(redactionEvent: Event<RedactionEventContent>) {
        if (redactionEvent is Event.MessageEvent) {
            val roomId = redactionEvent.roomId
            log.debug { "redact event with id ${redactionEvent.content.redacts} in room $roomId" }
            roomTimelineStore.update(redactionEvent.content.redacts, roomId) { oldTimelineEvent ->
                if (oldTimelineEvent != null) {
                    when (val oldEvent = oldTimelineEvent.event) {
                        is Event.MessageEvent -> {
                            redactRelation(oldEvent)
                            val eventType =
                                api.eventContentSerializerMappings.message
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            val newContent = RedactedMessageEventContent(eventType)
                            oldTimelineEvent.copy(
                                event = Event.MessageEvent(
                                    newContent,
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedRoomEventData.UnsignedMessageEventData(
                                        redactedBecause = redactionEvent
                                    )
                                ),
                                content = Result.success(newContent),
                            )
                        }

                        is Event.StateEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.state
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            val newContent = RedactedStateEventContent(eventType)
                            oldTimelineEvent.copy(
                                event = Event.StateEvent(
                                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.7/rooms/v9/#redactions
                                    newContent,
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedRoomEventData.UnsignedStateEventData(
                                        redactedBecause = redactionEvent
                                    ),
                                    oldEvent.stateKey,
                                ),
                                content = Result.success(newContent),
                            )
                        }
                    }
                } else null
            }
        }
    }

    private suspend fun List<TimelineEvent>.alsoAddRelationFromTimelineEvents() = also { events ->
        events.asFlow().map { it.event }.filterIsInstance<Event.MessageEvent<MessageEventContent>>()
            .collect(::addRelation)
    }

    internal suspend fun addRelation(event: Event<MessageEventContent>) {
        if (event is Event.MessageEvent) {
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
    }

    internal suspend fun redactRelation(redactedEvent: Event.MessageEvent<*>) {
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
}