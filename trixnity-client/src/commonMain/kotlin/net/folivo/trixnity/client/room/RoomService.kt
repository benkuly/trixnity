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
    ): Flow<Flow<TimelineEvent?>?>

    /**
     * Returns a flow of timeline events wrapped in a flow, which emits, when there is a new timeline event.
     *
     * To convert it to a flow of list, [toFlowList] can be used.
     *
     * Consuming this flow directly needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     *
     * Consider using [minSize] and [maxSize] when consuming this flow directly (e.g. with `toList()`). This can work
     * like paging through the timeline.
     *
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
        minSize: Int? = null,
        maxSize: Int? = null,
    ): Flow<Flow<TimelineEvent?>>

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
        minSize: Int? = null,
        maxSize: Int? = null,
    ): Flow<Flow<Flow<TimelineEvent?>>?>

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
    private val currentSyncState: CurrentSyncState,
    private val userInfo: UserInfo,
    private val scope: CoroutineScope,
) : RoomService {

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
                                log.info { "cannot find TimelineEvent $eventId in store. we try to fetch it by filling some gaps." }
                                getTimelineEvents(
                                    startFrom = lastEventId,
                                    roomId = roomId,
                                    direction = BACKWARDS,
                                    decryptionTimeout = ZERO,
                                    fetchTimeout = fetchTimeout,
                                    limitPerFetch = limitPerFetch
                                ).map { it.first() }.first { it?.eventId == eventId }
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
            getTimelineEvent(it, event.roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): Flow<Flow<TimelineEvent?>?> {
        return roomStore.get(roomId).transformLatest { room ->
            coroutineScope {
                if (room?.lastEventId != null) emit(getTimelineEvent(room.lastEventId, roomId, decryptionTimeout))
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
        minSize: Int?,
        maxSize: Int?,
    ): Flow<Flow<TimelineEvent?>> =
        channelFlow {
            fun TimelineEvent.Gap?.hasGap() =
                this != null && (this.hasGapBoth
                        || direction == FORWARDS && this.hasGapAfter
                        || direction == BACKWARDS && this.hasGapBefore)

            var currentTimelineEventFlow: Flow<TimelineEvent?> =
                getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
            send(currentTimelineEventFlow)
            var size = 1
            while (isActive) {
                currentTimelineEventFlow = currentTimelineEventFlow
                    .filterNotNull()
                    .onEach { currentTimelineEvent ->
                        val gap = currentTimelineEvent.gap
                        if (gap.hasGap()) {
                            log.debug { "found $gap at ${currentTimelineEvent.eventId}" }
                            fillTimelineGaps(currentTimelineEvent.eventId, currentTimelineEvent.roomId, limitPerFetch)
                        }
                    }
                    .filter { it.gap.hasGap().not() }
                    .mapNotNull { currentTimelineEvent ->
                        when (direction) {
                            BACKWARDS -> getPreviousTimelineEvent(currentTimelineEvent, decryptionTimeout, ZERO)
                            FORWARDS -> getNextTimelineEvent(currentTimelineEvent, decryptionTimeout, ZERO)
                        }
                    }
                    .first()
                send(currentTimelineEventFlow)
                size++
                if (direction == BACKWARDS
                    && currentTimelineEventFlow.first().let { it?.isFirst == true }
                ) {
                    log.debug { "reached start of timeline $roomId" }
                    break
                }
                if (minSize != null && size >= minSize
                    && currentTimelineEventFlow.first().let { it?.gap?.hasGap() == true }
                ) {
                    log.debug { "found a gap and complete flow, because minSize reached" }
                    break
                }
                if (maxSize != null && size >= maxSize) {
                    log.debug { "complete flow because maxSize reached" }
                    break
                }
            }
        }.buffer(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        minSize: Int?,
        maxSize: Int?,
    ): Flow<Flow<Flow<TimelineEvent?>>?> =
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