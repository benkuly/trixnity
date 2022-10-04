package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import com.soywiz.korio.async.async
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.AfterSyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
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

interface IRoomService {
    suspend fun fillTimelineGaps(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    )

    /**
     * Returns the [TimelineEvent] and starts decryption with the given [CoroutineScope]. If it is not found locally, it is tried
     * to find it by filling the sync-gaps.
     */
    suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): StateFlow<TimelineEvent?>

    suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): StateFlow<TimelineEvent?>?

    suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): StateFlow<TimelineEvent?>?

    suspend fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
    ): Flow<StateFlow<TimelineEvent?>?>

    /**
     * Returns a flow of timeline events wrapped in a flow, which emits, when there is a new timeline event
     * at the end of the timeline.
     *
     * To convert it to a list, [toFlowList] can be used or e.g. the events can be consumed manually.
     *
     * The manual approach needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     */
    suspend fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction = BACKWARDS,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<StateFlow<TimelineEvent?>>

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
    suspend fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<Flow<StateFlow<TimelineEvent?>>?>

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
    suspend fun getTimelineEventsAround(
        startFrom: EventId,
        roomId: RoomId,
        maxSizeBefore: StateFlow<Int>,
        maxSizeAfter: StateFlow<Int>,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = 1.minutes,
        limitPerFetch: Long = 20,
    ): Flow<List<StateFlow<TimelineEvent?>>>

    suspend fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        scope: CoroutineScope,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?>

    suspend fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
        scope: CoroutineScope,
    ): Flow<Set<TimelineEventRelation>?>

    suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit)

    suspend fun abortSendMessage(transactionId: String)

    suspend fun retrySendMessage(transactionId: String)
    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>>

    suspend fun getById(roomId: RoomId): StateFlow<Room?>

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): Flow<C?>

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): C?

    fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>>

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Event<C>?>

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
    ): Event<C>?

    suspend fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Map<String, Event<C>?>?>

    suspend fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Map<String, Event<C>?>?

    suspend fun canBeRedacted(
        timelineEvent: TimelineEvent,
    ): Flow<Boolean>
}

class RoomService(
    private val api: IMatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val roomEventDecryptionServices: List<RoomEventDecryptionService>,
    private val mediaService: IMediaService,
    private val timelineEventHandler: ITimelineEventHandler,
    private val currentSyncState: CurrentSyncState,
    private val userInfo: UserInfo,
    private val scope: CoroutineScope,
) : IRoomService {

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
    override suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): StateFlow<TimelineEvent?> {
        return roomTimelineStore.get(eventId, roomId, coroutineScope).also { timelineEventFlow ->
            coroutineScope.launch {
                val timelineEvent = timelineEventFlow.value ?: withTimeoutOrNull(fetchTimeout) {
                    val lastEventId = roomStore.get(roomId).value?.lastEventId
                    if (lastEventId != null) {
                        log.info { "cannot find TimelineEvent $eventId in store. we try to fetch it by filling some gaps." }
                        getTimelineEvents(
                            startFrom = lastEventId,
                            roomId = roomId,
                            direction = BACKWARDS,
                            decryptionTimeout = ZERO,
                            fetchTimeout = fetchTimeout,
                            limitPerFetch = limitPerFetch
                        ).map { it.value }.first { it?.eventId == eventId }
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
        }
    }

    override suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): StateFlow<TimelineEvent?>? {
        return event.previousEventId?.let {
            getTimelineEvent(it, event.roomId, coroutineScope, decryptionTimeout, fetchTimeout, limitPerFetch)
        }
    }

    override suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): StateFlow<TimelineEvent?>? {
        return event.nextEventId?.let {
            getTimelineEvent(it, event.roomId, coroutineScope, decryptionTimeout, fetchTimeout, limitPerFetch)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): Flow<StateFlow<TimelineEvent?>?> {
        return roomStore.get(roomId).transformLatest { room ->
            coroutineScope {
                if (room?.lastEventId != null) emit(getTimelineEvent(room.lastEventId, roomId, this, decryptionTimeout))
                else emit(null)
                delay(INFINITE) // ensure, that the TimelineEvent does not get removed from cache
            }
        }.distinctUntilChanged()
    }

    override suspend fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<StateFlow<TimelineEvent?>> =
        channelFlow {
            fun TimelineEvent.Gap?.hasGap() =
                this != null && (this.hasGapBoth
                        || direction == FORWARDS && this.hasGapAfter
                        || direction == BACKWARDS && this.hasGapBefore)

            var currentTimelineEventFlow: StateFlow<TimelineEvent?> =
                getTimelineEvent(startFrom, roomId, this, decryptionTimeout, fetchTimeout, limitPerFetch)
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
                            BACKWARDS -> getPreviousTimelineEvent(currentTimelineEvent, this, decryptionTimeout, ZERO)
                            FORWARDS -> getNextTimelineEvent(currentTimelineEvent, this, decryptionTimeout, ZERO)
                        }
                    }
                    .first()
                send(currentTimelineEventFlow)
            } while (isActive && (direction != BACKWARDS || currentTimelineEventFlow.value.let { it == null || it.isFirst.not() }))
            log.info { "reached start of timeline $roomId" }
            close()
        }.buffer(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<Flow<StateFlow<TimelineEvent?>>?> =
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
                        getTimelineEvent(it.id, it.roomId, this, decryptionTimeout)
                    }
                }.asFlow()
                    .map {
                        it.await().value
                    }.filterNotNull()
            }
        }

    override suspend fun getTimelineEventsAround(
        startFrom: EventId,
        roomId: RoomId,
        maxSizeBefore: StateFlow<Int>,
        maxSizeAfter: StateFlow<Int>,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<List<StateFlow<TimelineEvent?>>> = channelFlow {
        val startEvent = getTimelineEvent(startFrom, roomId, this, decryptionTimeout, fetchTimeout, limitPerFetch)
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

    override suspend fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        scope: CoroutineScope,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?> = roomTimelineStore.getRelations(eventId, roomId, scope)

    override suspend fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
        scope: CoroutineScope,
    ): Flow<Set<TimelineEventRelation>?> = roomTimelineStore.getRelations(eventId, roomId, relationType, scope)


    override suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit) {
        val isEncryptedRoom = roomStore.get(roomId).value?.encryptionAlgorithm == Megolm
        val content = MessageBuilder(isEncryptedRoom, mediaService).build(builder)
        requireNotNull(content)
        val transactionId = uuid4().toString()
        roomOutboxMessageStore.update(transactionId) {
            RoomOutboxMessage(
                transactionId = transactionId,
                roomId = roomId,
                content = content,
                sentAt = null,
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

    override suspend fun getById(roomId: RoomId): StateFlow<Room?> {
        return roomStore.get(roomId)
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
        scope: CoroutineScope
    ): Flow<C?> {
        return roomAccountDataStore.get(roomId, eventContentClass, key, scope)
            .map { it?.content }
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
    ): C? {
        return roomAccountDataStore.get(roomId, eventContentClass, key)?.content
    }

    override fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> = roomOutboxMessageStore.getAll()

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Event<C>?> {
        return roomStateStore.getByStateKey(roomId, stateKey, eventContentClass, scope)
    }

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Event<C>? {
        return roomStateStore.getByStateKey(roomId, stateKey, eventContentClass)
    }

    override suspend fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Map<String, Event<C>?>?> {
        return roomStateStore.get(roomId, eventContentClass, scope)
    }

    override suspend fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Map<String, Event<C>?>? {
        return roomStateStore.get(roomId, eventContentClass)
    }

    override suspend fun canBeRedacted(
        timelineEvent: TimelineEvent,
    ): Flow<Boolean> {
        return channelFlow {
            roomStateStore.getByStateKey(timelineEvent.roomId, "", PowerLevelsEventContent::class, this)
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
                }.collect {
                    send(it)
                }
        }
    }

}