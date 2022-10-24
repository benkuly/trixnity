package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import com.soywiz.korio.async.async
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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
     * Returns a flow of timeline events wrapped in a flow, which emits, when there is a new timeline event
     * at the end of the timeline.
     *
     * To convert it to a list, [toFlowList] can be used or e.g. the events can be consumed manually.
     *
     * The manual approach needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     */
    fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction = BACKWARDS,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<Flow<TimelineEvent?>>

    /**
     * Returns the last timeline events as flow.
     *
     * To convert it to a list, [toFlowList] can be used or e.g. the events can be consumed manually:
     * ```kotlin
     * launch {
     *   matrixClient.room.getLastTimelineEvents(roomId).collectLatest { timelineEventsFlow ->
     *     timelineEventsFlow?.take(10)?.toList()?.reversed()?.forEach { println(it) }
     *   }
     * }
     * ```
     * The manual approach needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     */
    fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
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

    /**
     * Returns all timeline events around a starting event. This also triggers decryption for each timeline event.
     *
     * The size of the returned list can be expanded in 2 directions: before and after the start element.
     *
     * @param startFrom the start event id
     * @param maxSizeBefore how many events to possibly get before the start event
     * @param maxSizeAfter how many events to possibly get after the start event
     *
     */
    fun getTimelineEventsAround(
        startFrom: EventId,
        roomId: RoomId,
        maxSizeBefore: StateFlow<Int>,
        maxSizeAfter: StateFlow<Int>,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<List<Flow<TimelineEvent?>>>

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

    /**
     * @param coroutineScope The [CoroutineScope] is used to fetch and/or decrypt the [TimelineEvent] and to determine,
     * how long the [TimelineEvent] should be hold in cache.
     */
    override fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<TimelineEvent?> = channelFlow {
        roomTimelineStore.get(eventId, roomId).also { timelineEventFlow ->
            launch {
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
                        roomTimelineStore.update(eventId, roomId, persistIntoRepository = false) { oldEvent ->
                            // we check here again, because an event could be redacted at the same time
                            if (oldEvent?.canBeDecrypted() == true) timelineEvent.copy(content = decryptedEventContent)
                            else oldEvent
                        }
                    }
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
    ): Flow<Flow<TimelineEvent?>> =
        channelFlow {
            fun TimelineEvent.Gap?.hasGap() =
                this != null && (this.hasGapBoth
                        || direction == FORWARDS && this.hasGapAfter
                        || direction == BACKWARDS && this.hasGapBefore)

            var currentTimelineEventFlow: Flow<TimelineEvent?> =
                getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
            send(currentTimelineEventFlow)
            do {
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
            } while (isActive && (direction != BACKWARDS || currentTimelineEventFlow.first()
                    .let { it == null || it.isFirst.not() })
            )
            log.info { "reached start of timeline $roomId" }
            close()
        }.buffer(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<Flow<Flow<TimelineEvent?>>?> =
        roomStore.get(roomId)
            .mapLatest { it?.lastEventId }
            .distinctUntilChanged()
            .mapLatest {
                if (it != null) getTimelineEvents(it, roomId, BACKWARDS, decryptionTimeout, fetchTimeout, limitPerFetch)
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
                        withTimeoutOrNull(decryptionTimeout) {
                            timelineEventFlow.await().first { it?.content != null }
                        } ?: timelineEventFlow.await().first()
                    }.filterNotNull()
            }
        }

    override fun getTimelineEventsAround(
        startFrom: EventId,
        roomId: RoomId,
        maxSizeBefore: StateFlow<Int>,
        maxSizeAfter: StateFlow<Int>,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<List<Flow<TimelineEvent?>>> = channelFlow {
        val startEvent = getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch)
        startEvent.filterNotNull().first()
        combine(
            getTimelineEvents(startFrom, roomId, BACKWARDS, decryptionTimeout, fetchTimeout, limitPerFetch)
                .drop(1)
                .toFlowList(maxSizeBefore),
            getTimelineEvents(startFrom, roomId, FORWARDS, decryptionTimeout, fetchTimeout, limitPerFetch)
                .drop(1)
                .toFlowList(maxSizeAfter)
                .map { it.reversed() },
        ) { beforeElements, afterElements ->
            afterElements + startEvent + beforeElements
        }.collectLatest { send(it) }
    }.buffer(0)

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