package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import com.soywiz.korio.async.async
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.AfterSyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface RoomService {
    val usersTyping: StateFlow<Map<RoomId, TypingEventContent>>
    suspend fun fillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    )

    /**
     * Returns the [TimelineEvent] and starts decryption with the given [CoroutineScope]. If it is not found locally, it is tried
     * to find it by filling the sync-gaps.
     */
    fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<TimelineEvent?>

    fun getPreviousTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<TimelineEvent?>?

    fun getNextTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<TimelineEvent?>?

    fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
    ): Flow<Flow<TimelineEvent>?>

    /**
     * Returns a flow of timeline events wrapped in a flow. It emits, when there is a new timeline event. This flow
     * only completes, when the start of the timeline is reached or [minSize] and/or [maxSize] are set and reached.
     *
     * Consuming this flow directly needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     *
     * Consider using [minSize] and [maxSize] when consuming this flow directly (e.g. with `toList()`). This can work
     * like paging through the timeline. It also completes the flow, which is not the case, when both parameters are null.
     *
     * To convert it to a flow of list, [toFlowList] can be used.
     *
     * @param limitPerFetch The count of events requested from the server, when there is a gap.
     * @param minSize Flow completes, when a gap is found and this size is reached (including the start event).
     * @param maxSize Flow completes, when this value is reached (including the start event).
     */
    fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction = BACKWARDS,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
        minSize: Long? = null,
        maxSize: Long? = null,
    ): Flow<Flow<TimelineEvent>>

    /**
     * Returns the last timeline events as flow.
     *
     * To convert it to a flow of list, [toFlowList] can be used.
     *
     * @see [getTimelineEvents]
     */
    fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
        minSize: Long? = null,
        maxSize: Long? = null,
    ): Flow<Flow<Flow<TimelineEvent>>?>

    /**
     * Returns all timeline events from the moment this method is called. This also triggers decryption for each timeline event.
     *
     * It is possible, that the matrix server does not send all timeline events.
     * These gaps in the timeline are not filled automatically. Gap filling is available in
     * [getTimelineEvents] and [getLastTimelineEvents].
     *
     * @param syncResponseBufferSize the size of the buffer for consuming the sync response. When set to 0, the sync will
     * be suspended until all events from the sync response are consumed. This could prevent decryption, because keys may
     * be received in a later sync response.
     */
    fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration = 30.seconds,
        syncResponseBufferSize: Int = 10,
    ): Flow<TimelineEvent>

    /**
     * Returns a [Timeline] for a room.
     *
     * @param loadingSize When using [Timeline.init], [Timeline.loadBefore] or [Timeline.loadAfter] this is the max size
     * of events, that are added to the timeline. This refers to events found locally. Use [limitPerFetch], when you want
     * to control the size of events requests from the server.
     */
    fun <T> getTimeline(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
        loadingSize: Long = 20,
        transformer: suspend (Flow<TimelineEvent>) -> T,
    ): Timeline<T>

    fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?>

    fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Set<TimelineEventRelation>?>

    suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean = true,
        builder: suspend MessageBuilder.() -> Unit
    )

    suspend fun abortSendMessage(transactionId: String)

    suspend fun retrySendMessage(transactionId: String)
    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>>

    fun getById(roomId: RoomId): Flow<Room?>

    fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>

    fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>>

    fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
    ): Flow<Event<C>?>

    fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Event<C>?>?>

    fun canBeRedacted(
        timelineEvent: TimelineEvent,
    ): Flow<Boolean>
}

class RoomServiceImpl(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val roomEventDecryptionServices: List<RoomEventDecryptionService>,
    private val mediaService: MediaService,
    private val timelineEventHandler: TimelineEventHandler,
    typingEventHandler: TypingEventHandler,
    private val currentSyncState: CurrentSyncState,
    private val userInfo: UserInfo,
    private val scope: CoroutineScope,
) : RoomService {
    override val usersTyping: StateFlow<Map<RoomId, TypingEventContent>> = typingEventHandler.usersTyping

    override suspend fun fillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long
    ) {
        scope.async {
            currentSyncState.retryWhenSyncIs(
                RUNNING,
                onError = { log.error(it) { "could not fill gap starting from event $startEventId" } },
            ) {
                timelineEventHandler.unsafeFillTimelineGaps(startEventId, roomId, limit).getOrThrow()
            }
        }.await()
    }


    private fun TimelineEvent.canBeDecrypted(): Boolean =
        this.event is MessageEvent
                && this.event.isEncrypted
                && this.content == null

    private val getTimelineEventMutex = MutableStateFlow<Map<Pair<EventId, RoomId>, Mutex>>(mapOf())

    /**
     * @param coroutineScope The [CoroutineScope] is used to fetch and/or decrypt the [TimelineEvent] and to determine,
     * how long the [TimelineEvent] should be hold in cache.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<TimelineEvent?> = channelFlow {
        roomTimelineStore.get(eventId, roomId)
            .transformLatest { timelineEvent ->
                val event = timelineEvent?.event
                if (event is MessageEvent) {
                    val replacedBy = event.unsigned?.aggregations?.replace
                    if (replacedBy != null) {
                        emitAll(getTimelineEvent(replacedBy.eventId, roomId)
                            .map { replacedByTimelineEvent ->
                                val newContent =
                                    replacedByTimelineEvent?.content
                                        ?.map { content ->
                                            if (content is MessageEventContent) {
                                                val relatesTo = content.relatesTo
                                                if (relatesTo is RelatesTo.Replace) relatesTo.newContent
                                                else null
                                            } else null
                                        }
                                        ?.mapCatching {
                                            if (it == null) {
                                                log.warn { "could not find replacing event content for $eventId in $roomId" }
                                                throw IllegalStateException("replacing event did not contain replace")
                                            } else it
                                        } ?: timelineEvent.content
                                timelineEvent.copy(content = newContent)
                            })
                    } else emit(timelineEvent)
                } else emit(timelineEvent)
            }
            .also { timelineEventFlow ->
                launch {
                    val key = eventId to roomId
                    val mutex = getTimelineEventMutex.updateAndGet {
                        if (it.containsKey(key)) it else it + (key to Mutex())
                    }[key]
                    requireNotNull(mutex)
                    mutex.withLock {
                        val timelineEvent = timelineEventFlow.first() ?: withTimeoutOrNull(fetchTimeout) {
                            val lastEventId = roomStore.get(roomId).first()?.lastEventId
                            if (lastEventId != null) {
                                log.debug { "cannot find TimelineEvent $eventId in store. we try to fetch it by filling some gaps." }
                                getTimelineEvents(
                                    startFrom = lastEventId,
                                    roomId = roomId,
                                    direction = BACKWARDS,
                                    decryptionTimeout = ZERO,
                                    fetchTimeout = fetchTimeout,
                                    limitPerFetch = limitPerFetch
                                ).map { it.first() }.firstOrNull { it.eventId == eventId }
                                    .also { log.trace { "found TimelineEvent $eventId" } }
                            } else null
                        }
                        if (timelineEvent?.canBeDecrypted() == true) {
                            val decryptedEventContent = withTimeoutOrNull(decryptionTimeout) {
                                roomEventDecryptionServices.firstNotNullOfOrNull { it.decrypt(timelineEvent.event) }
                            }
                            if (decryptedEventContent != null) {
                                roomTimelineStore.update(
                                    eventId,
                                    roomId,
                                    persistIntoRepository = false
                                ) { oldEvent ->
                                    // we check here again, because an event could be redacted at the same time
                                    if (oldEvent?.canBeDecrypted() == true) timelineEvent.copy(content = decryptedEventContent)
                                    else oldEvent
                                }
                            }
                        }
                        getTimelineEventMutex.update { it - key }
                    }
                }
            }.collect { send(it) }
    }

    override fun getPreviousTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<TimelineEvent?>? {
        return event.previousEventId?.let {
            getTimelineEvent(it, event.roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
        }
    }

    override fun getNextTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<TimelineEvent?>? {
        return event.nextEventId?.let {
            getTimelineEvent(
                eventId = it,
                roomId = event.roomId,
                decryptionTimeout = decryptionTimeout,
                fetchTimeout = fetchTimeout,
                limitPerFetch = limitPerFetch
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): Flow<Flow<TimelineEvent>?> {
        return roomStore.get(roomId).transformLatest { room ->
            coroutineScope {
                if (room?.lastEventId != null) emit(
                    getTimelineEvent(
                        room.lastEventId,
                        roomId,
                        decryptionTimeout
                    ).filterNotNull()
                )
                else emit(null)
                delay(INFINITE) // ensure, that the TimelineEvent does not get removed from cache
            }
        }.distinctUntilChanged()
    }

    override fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        minSize: Long?,
        maxSize: Long?,
    ): Flow<Flow<TimelineEvent>> =
        channelFlow {
            val loopDetectionEventIds = mutableListOf(startFrom)
            fun TimelineEvent.needsFetchGap(): Boolean {
                return gap != null && (gap.hasGapBoth && isLast.not() && isFirst.not()
                        || direction == FORWARDS && gap.hasGapAfter && isLast.not()
                        || direction == BACKWARDS && gap.hasGapBefore && isFirst.not())
            }

            var currentTimelineEventFlow: Flow<TimelineEvent?> =
                getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
            send(currentTimelineEventFlow.filterNotNull())
            var size = 1
            while (isActive) {
                // check for break conditions
                val timelineEventSnapshot = currentTimelineEventFlow.filterNotNull().first()
                log.trace { "getTimelineEvents: size=$size minSize=$minSize maxSize=$maxSize direction=${direction.name} currentTimelineEvent=$timelineEventSnapshot" }
                if (direction == BACKWARDS && timelineEventSnapshot.isFirst) {
                    log.debug { "getTimelineEvents: reached start of timeline $roomId" }
                    break
                }
                if (minSize != null && size >= minSize
                    && (timelineEventSnapshot.needsFetchGap() || (direction == FORWARDS && timelineEventSnapshot.isLast))
                ) {
                    log.debug { "getTimelineEvents: found a gap and complete flow, because minSize reached" }
                    break
                }
                if (maxSize != null && size >= maxSize) {
                    log.debug { "getTimelineEvents: complete flow because maxSize reached" }
                    break
                }

                currentTimelineEventFlow = currentTimelineEventFlow
                    .filterNotNull()
                    .onEach { currentTimelineEvent ->
                        if (currentTimelineEvent.needsFetchGap()) {
                            log.debug { "found ${currentTimelineEvent.gap} at ${currentTimelineEvent.eventId}" }
                            fillTimelineGaps(currentTimelineEvent.eventId, currentTimelineEvent.roomId, limitPerFetch)
                        }
                    }
                    .filter { it.needsFetchGap().not() }
                    .mapNotNull { currentTimelineEvent ->
                        when (direction) {
                            BACKWARDS -> getPreviousTimelineEvent(
                                event = currentTimelineEvent,
                                decryptionTimeout = decryptionTimeout,
                                fetchTimeout = ZERO
                            )

                            FORWARDS -> getNextTimelineEvent(
                                event = currentTimelineEvent,
                                decryptionTimeout = decryptionTimeout,
                                fetchTimeout = ZERO
                            )
                        }
                    }
                    .first()
                    .filterNotNull()

                // check for loop
                val newTimelineEventSnapshot = currentTimelineEventFlow.first()
                if (loopDetectionEventIds.contains(newTimelineEventSnapshot.eventId)) {
                    val message =
                        "Detected a loop in timeline generation. " +
                                "Event with id ${newTimelineEventSnapshot.eventId} has already be emitted in this flow. " +
                                "This is a severe misbehavior and must be fixed in Trixnity!!!"
                    log.error { message } // log even when a consumer don't catch the exception
                    throw IllegalStateException(message)
                } else loopDetectionEventIds.add(newTimelineEventSnapshot.eventId)

                send(currentTimelineEventFlow)
                size++
            }
        }.buffer(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        minSize: Long?,
        maxSize: Long?,
    ): Flow<Flow<Flow<TimelineEvent>>?> =
        roomStore.get(roomId)
            .mapLatest { it?.lastEventId }
            .distinctUntilChanged()
            .mapLatest {
                if (it != null) getTimelineEvents(
                    startFrom = it,
                    roomId = roomId,
                    direction = BACKWARDS,
                    decryptionTimeout = decryptionTimeout,
                    fetchTimeout = fetchTimeout,
                    limitPerFetch = limitPerFetch,
                    minSize = minSize,
                    maxSize = maxSize
                )
                else null
            }

    @OptIn(FlowPreview::class)
    override fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<TimelineEvent> =
        callbackFlow {
            val subscriber: AfterSyncResponseSubscriber = { send(it) }
            api.sync.subscribeAfterSyncResponse(subscriber)
            awaitClose { api.sync.unsubscribeAfterSyncResponse(subscriber) }
        }.flatMapConcat { syncResponse ->
            coroutineScope {
                val timelineEvents =
                    syncResponse.room?.join?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty() +
                            syncResponse.room?.leave?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty()
                timelineEvents.map {
                    async {
                        getTimelineEvent(it.id, it.roomId, decryptionTimeout)
                    }
                }.asFlow()
                    .map { timelineEventFlow ->
                        // we must wait until TimelineEvent is saved into store
                        val notNullTimelineEvent = timelineEventFlow.await().filterNotNull().first()
                        withTimeoutOrNull(decryptionTimeout) {
                            timelineEventFlow.await().filterNotNull().first { it.content != null }
                        } ?: notNullTimelineEvent
                    }
            }
        }

    override fun <T> getTimeline(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        loadingSize: Long,
        transformer: suspend (Flow<TimelineEvent>) -> T,
    ): Timeline<T> =
        TimelineImpl(
            roomId = roomId,
            decryptionTimeout = decryptionTimeout,
            fetchTimeout = fetchTimeout,
            limitPerFetch = limitPerFetch,
            loadingSize = loadingSize,
            roomService = this,
            transformer = transformer,
        )

    override fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?> = roomTimelineStore.getRelations(eventId, roomId)

    override fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Set<TimelineEventRelation>?> = roomTimelineStore.getRelations(eventId, roomId, relationType)


    override suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean,
        builder: suspend MessageBuilder.() -> Unit
    ) {
        val isEncryptedRoom = roomStore.get(roomId).first()?.encryptionAlgorithm == Megolm
        val content = MessageBuilder(isEncryptedRoom, mediaService).build(builder)
        requireNotNull(content)
        val transactionId = uuid4().toString()
        roomOutboxMessageStore.update(transactionId) {
            RoomOutboxMessage(
                transactionId = transactionId,
                roomId = roomId,
                content = content,
                sentAt = null,
                keepMediaInCache = keepMediaInCache,
                mediaUploadProgress = MutableStateFlow(null)
            )
        }
    }

    override suspend fun abortSendMessage(transactionId: String) {
        roomOutboxMessageStore.update(transactionId) { null }
    }

    override suspend fun retrySendMessage(transactionId: String) {
        roomOutboxMessageStore.update(transactionId) { it?.copy(retryCount = 0) }
    }

    override fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> = roomStore.getAll()

    override fun getById(roomId: RoomId): Flow<Room?> {
        return roomStore.get(roomId)
    }

    override fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return roomAccountDataStore.get(roomId, eventContentClass, key)
            .map { it?.content }
    }

    override fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> = roomOutboxMessageStore.getAll()

    override fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Flow<Event<C>?> {
        return roomStateStore.getByStateKey(roomId, stateKey, eventContentClass)
    }

    override fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Event<C>?>?> {
        return roomStateStore.get(roomId, eventContentClass)
    }

    override fun canBeRedacted(
        timelineEvent: TimelineEvent,
    ): Flow<Boolean> {
        return roomStateStore.getByStateKey(timelineEvent.roomId, "", PowerLevelsEventContent::class)
            .filterNotNull()
            .map { it.content }
            .map { powerLevels ->
                val userPowerLevel = powerLevels.users[userInfo.userId] ?: powerLevels.usersDefault
                val sendRedactionEventPowerLevel =
                    powerLevels.events["m.room.redaction"] ?: powerLevels.eventsDefault
                val redactPowerLevelNeeded = powerLevels.redact
                val ownMessages = userPowerLevel >= sendRedactionEventPowerLevel
                val otherMessages = userPowerLevel >= redactPowerLevelNeeded
                val content = timelineEvent.content?.getOrNull()
                content is MessageEventContent && content !is RedactedMessageEventContent &&
                        (timelineEvent.event.sender == userInfo.userId && ownMessages || otherMessages)
            }
    }

}