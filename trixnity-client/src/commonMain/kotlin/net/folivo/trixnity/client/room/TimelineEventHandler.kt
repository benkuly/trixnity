package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

interface ITimelineEventHandler {
    suspend fun unsafeFillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    ): Result<Unit>
}

class TimelineEventHandler(
    private val userInfo: UserInfo,
    private val api: IMatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val lastRelevantEventFilter: LastRelevantEventFilter,
    private val timelineMutex: TimelineMutex,
) : EventHandler, ITimelineEventHandler {
    companion object {
        const val LAZY_LOAD_MEMBERS_FILTER = """{"lazy_load_members":true}"""
    }

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::redactTimelineEvent)
        api.sync.subscribe(::addRelation)
        api.sync.subscribeSyncResponse(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::redactTimelineEvent)
            api.sync.unsubscribe(::addRelation)
            api.sync.unsubscribeSyncResponse(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            roomStore.update(roomId) {
                it?.copy(membership = Membership.JOIN) ?: Room(
                    roomId = roomId,
                    membership = Membership.JOIN
                )
            }
            room.value.unreadNotifications?.notificationCount?.also { setUnreadMessageCount(roomId, it) }
            room.value.timeline?.also {
                timelineMutex.withTransactionalLock(room.key) {
                    addEventsToTimelineAtEnd(
                        roomId = roomId,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.also { event -> setLastEventId(event) }
                    it.events?.forEach { event ->
                        syncOutboxMessage(event)
                        setLastRelevantEvent(event)
                    }
                }
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            roomStore.update(room.key) {
                it?.copy(membership = Membership.LEAVE) ?: Room(
                    room.key,
                    membership = Membership.LEAVE
                )
            }
            room.value.timeline?.also {
                timelineMutex.withTransactionalLock(room.key) {
                    addEventsToTimelineAtEnd(
                        roomId = room.key,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.let { event -> setLastEventId(event) }
                    it.events?.forEach { event -> setLastRelevantEvent(event) }
                }
            }
        }
        syncResponse.room?.knock?.entries?.forEach { (room, _) ->
            roomStore.update(room) {
                it?.copy(membership = Membership.KNOCK) ?: Room(
                    room,
                    membership = Membership.KNOCK
                )
            }
        }
        syncResponse.room?.invite?.entries?.forEach { (room, _) ->
            roomStore.update(room) {
                it?.copy(membership = Membership.INVITE) ?: Room(
                    room,
                    membership = Membership.INVITE
                )
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
            val room = roomStore.get(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            suspend fun useDecryptedOutboxMessagesForOwnTimelineEvents(timelineEvents: List<TimelineEvent>) =
                timelineEvents.alsoAddRelationFromTimelineEvents().map {
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
                previousEvent = room.lastEventId,
                previousEventChunk = null,
                nextToken = nextBatch,
                nextHasGap = true,
                nextEvent = null,
                nextEventChunk = events.drop(1),
                processTimelineEventsBeforeSave = ::useDecryptedOutboxMessagesForOwnTimelineEvents
            )
        }
    }

    private suspend fun List<Event.RoomEvent<*>>.findPreviousEvent() =
        asFlow()
            .mapNotNull { roomTimelineStore.get(it.id, it.roomId, withTransaction = false) }
            .firstOrNull { it.gap?.hasGapAfter == true }
            ?.also { log.trace { "previousEvent ${it.eventId} found in chunk" } }

    private suspend fun List<Event.RoomEvent<*>>.findNextEvent() =
        asFlow()
            .mapNotNull { roomTimelineStore.get(it.id, it.roomId, withTransaction = false) }
            .firstOrNull { it.gap?.hasGapBefore == true }
            ?.also { log.trace { "nextEvent ${it.eventId} found in chunk" } }

    override suspend fun unsafeFillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long
    ): Result<Unit> = timelineMutex.withTransactionalLock(roomId) {
        kotlin.runCatching {
            val isLastEventId = roomStore.get(roomId).value?.lastEventId == startEventId

            val startEvent =
                roomTimelineStore.get(startEventId, roomId, withTransaction = false) ?: return@runCatching
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
                previousEvent = possiblyPreviousEvent?.eventId ?: response.chunk?.findPreviousEvent()?.eventId
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

            val possiblyNextEvent = roomTimelineStore.getNext(startEvent)
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
                nextEvent = possiblyNextEvent?.eventId ?: response.chunk?.findNextEvent()?.eventId
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

    internal suspend fun setLastEventId(event: Event<*>) {
        if (event is Event.RoomEvent) {
            roomStore.update(event.roomId, withTransaction = false) { oldRoom ->
                oldRoom?.copy(lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventId = event.id)
            }
        }
    }

    internal suspend fun setLastRelevantEvent(event: Event.RoomEvent<*>) {
        if (lastRelevantEventFilter(event))
            roomStore.update(event.roomId, withTransaction = false) { oldRoom ->
                oldRoom?.copy(lastRelevantEventId = event.id)
                    ?: Room(roomId = event.roomId, lastRelevantEventId = event.id)
            }
    }


    internal suspend fun setUnreadMessageCount(roomId: RoomId, count: Long) {
        roomStore.update(roomId) { oldRoom ->
            oldRoom?.copy(
                unreadMessageCount = count
            ) ?: Room(
                roomId = roomId,
                unreadMessageCount = count
            )
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
                                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.3/rooms/v9/#redactions
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

    internal suspend fun syncOutboxMessage(event: Event<*>) {
        if (event is Event.MessageEvent)
            if (event.sender == userInfo.userId) {
                event.unsigned?.transactionId?.also {
                    roomOutboxMessageStore.update(it) { null }
                }
            }
    }

    private suspend fun List<TimelineEvent>.alsoAddRelationFromTimelineEvents() = also { events ->
        events.asFlow().map { it.event }.filterIsInstance<Event<MessageEventContent>>()
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
                        relationType = relatesTo.type,
                        relatedEventId = relatesTo.eventId
                    )
                )
            }
        }
    }
}