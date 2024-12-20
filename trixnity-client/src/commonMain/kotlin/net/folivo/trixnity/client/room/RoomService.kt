package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.TimelineEventContentError
import net.folivo.trixnity.client.utils.retryWhenSyncIs
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.core.subscribeAsFlow
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.utils.KeyedMutex
import net.folivo.trixnity.utils.nextString
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface RoomService {
    val usersTyping: StateFlow<Map<RoomId, TypingEventContent>>
    suspend fun fillTimelineGaps(
        roomId: RoomId,
        startEventId: EventId,
        limit: Long = 20
    )

    /**
     * Returns the [TimelineEvent] and starts decryption. If it is not found locally, the algorithm will try to find
     * the event by traversing the events from the end of the timeline (i.e. from the last sent event).
     * This can include filling sync gaps from the server and thus might take a while.
     * Please consider changing the [config].
     */
    fun getTimelineEvent(
        roomId: RoomId,
        eventId: EventId,
        config: GetTimelineEventConfig.() -> Unit = {},
    ): Flow<TimelineEvent?>

    fun getPreviousTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit = {},
    ): Flow<TimelineEvent?>?

    fun getNextTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit = {},
    ): Flow<TimelineEvent?>?

    fun getLastTimelineEvent(
        roomId: RoomId,
        config: GetTimelineEventConfig.() -> Unit = {},
    ): Flow<Flow<TimelineEvent>?>

    /**
     * Returns a flow of timeline events wrapped in a flow. It emits, when there is a new timeline event. This flow
     * only completes, when the start of the timeline is reached or [GetTimelineEventsConfig.minSize] and/or
     * [GetTimelineEventsConfig.maxSize] are set and reached.
     *
     * Consuming this flow directly needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     *
     * Consider using [GetTimelineEventsConfig.minSize] and [GetTimelineEventsConfig.maxSize] when consuming this flow
     * directly (e.g. with `toList()`). This can work
     * like paging through the timeline. It also completes the flow, which is not the case, when both parameters are null.
     *
     * To convert it to a flow of list, [flattenValues] can be used.
     */
    fun getTimelineEvents(
        roomId: RoomId,
        startFrom: EventId,
        direction: Direction = BACKWARDS,
        config: GetTimelineEventsConfig.() -> Unit = {},
    ): Flow<Flow<TimelineEvent>>

    /**
     * Returns the last timeline events as flow.
     *
     * To convert it to a flow of list, [flattenValues] can be used.
     *
     * @see [getTimelineEvents]
     */
    fun getLastTimelineEvents(
        roomId: RoomId,
        config: GetTimelineEventsConfig.() -> Unit = {},
    ): Flow<Flow<Flow<TimelineEvent>>?>

    /**
     * Returns all timeline events from the moment this method is called. This also triggers decryption for each timeline event.
     *
     * It is possible, that the matrix server does not send all timeline events.
     * These gaps in the timeline are not filled automatically. Gap filling is available in
     * [getTimelineEvents] and [getLastTimelineEvents].
     *
     * @param syncResponseBufferSize the number of syncs that will be buffered. When set to 0, the sync will
     * be suspended until all events from the current sync response are consumed. This could prevent decryption,
     * because keys may be received in a later sync response.
     */
    fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration = 30.seconds,
        syncResponseBufferSize: Int = 4,
    ): Flow<TimelineEvent>

    /**
     * Returns a [Timeline] for a room.
     */
    fun <T> getTimeline(
        roomId: RoomId,
        transformer: suspend (Flow<TimelineEvent>) -> T,
    ): Timeline<T>

    fun getTimelineEventRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
    ): Flow<Map<EventId, Flow<TimelineEventRelation?>>?>

    /**
     * Puts a message to the outbox.
     *
     * @return The transaction id that was used to send the message.
     */
    suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean = true,
        builder: suspend MessageBuilder.() -> Unit
    ): String

    suspend fun cancelSendMessage(roomId: RoomId, transactionId: String)

    suspend fun retrySendMessage(roomId: RoomId, transactionId: String)

    /**
     * Upgraded rooms ([Room.hasBeenReplaced]) should not be rendered.
     *
     * [flattenValues] can be used to get rid of the nested flows.
     */
    fun getAll(): Flow<Map<RoomId, Flow<Room?>>>

    fun getById(roomId: RoomId): Flow<Room?>

    /**
     * If the room has [Membership.LEAVE], you can delete it locally.
     */
    suspend fun forgetRoom(roomId: RoomId)

    fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): Flow<C?>

    fun getOutbox(): Flow<List<Flow<RoomOutboxMessage<*>?>>>
    fun getOutbox(roomId: RoomId): Flow<List<Flow<RoomOutboxMessage<*>?>>>
    fun getOutbox(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?>

    fun <C : StateEventContent> getState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String = "",
    ): Flow<StateBaseEvent<C>?>

    fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<StateBaseEvent<C>?>>>
}

class RoomServiceImpl(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val mediaService: MediaService,
    private val forgetRoomService: ForgetRoomService,
    private val userInfo: UserInfo,
    private val timelineEventHandler: TimelineEventHandler,
    private val clock: Clock,
    private val config: MatrixClientConfiguration,
    typingEventHandler: TypingEventHandler,
    private val currentSyncState: CurrentSyncState,
    private val scope: CoroutineScope,
) : RoomService {
    override val usersTyping: StateFlow<Map<RoomId, TypingEventContent>> = typingEventHandler.usersTyping

    override suspend fun fillTimelineGaps(
        roomId: RoomId,
        startEventId: EventId,
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
                && (this.content == null || this.content.exceptionOrNull() is TimelineEventContentError.DecryptionTimeout)

    private val getTimelineEventFetchMutex = KeyedMutex<Pair<EventId, RoomId>>()
    private val getTimelineEventDecryptionMutex = KeyedMutex<Pair<EventId, RoomId>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTimelineEvent(
        roomId: RoomId,
        eventId: EventId,
        config: GetTimelineEventConfig.() -> Unit
    ): Flow<TimelineEvent?> {
        val cfg = GetTimelineEventConfig().apply(config).copy()
        return roomTimelineStore.get(eventId, roomId)
            .flatMapLatest { timelineEvent ->
                val event = timelineEvent?.event
                if (cfg.allowReplaceContent && event is MessageEvent) {
                    val replacedByFlow = getTimelineEventReplaceAggregation(roomId, eventId).map { it.replacedBy }
                    replacedByFlow.flatMapLatest { replacedBy ->
                        if (replacedBy != null) {
                            getTimelineEvent(roomId, replacedBy)
                                .map { replacedByTimelineEvent ->
                                    val newContent =
                                        replacedByTimelineEvent?.content
                                            ?.mapCatching { content ->
                                                val newContent = if (content is MessageEventContent) {
                                                    val relatesTo = content.relatesTo
                                                    if (relatesTo is RelatesTo.Replace) relatesTo.newContent
                                                    else null
                                                } else null
                                                newContent ?: throw TimelineEventContentError.NoContent
                                            } ?: timelineEvent.content
                                    timelineEvent.copy(content = newContent)
                                }
                        } else flowOf(timelineEvent)
                    }
                } else flowOf(timelineEvent)
            }
            .transformLatest { timelineEvent ->
                emit(timelineEvent)

                if (timelineEvent == null) {
                    withTimeoutOrNull(cfg.fetchTimeout) {
                        getTimelineEventFetchMutex.withLock(eventId to roomId) {
                            val lastEventId = roomStore.get(roomId).first()?.lastEventId
                            if (lastEventId != null) {
                                log.debug { "getTimelineEvent: cannot find TimelineEvent $eventId in store. we try to fetch it by filling some gaps." }
                                getTimelineEvents(
                                    startFrom = lastEventId,
                                    roomId = roomId,
                                    direction = BACKWARDS,
                                    config = {
                                        apply(cfg)
                                        decryptionTimeout = ZERO
                                    }
                                ).map { it.first() }.firstOrNull { it.eventId == eventId }
                                    .also { log.trace { "getTimelineEvent: found TimelineEvent $eventId" } }
                            } else null
                        }
                    }
                    log.warn { "getTimelineEvent: could not find TimelineEvent ${eventId} in store or by fetching (timeout=${cfg.fetchTimeout})" }
                } else {
                    val event = timelineEvent.event
                    if (cfg.decryptionTimeout > ZERO && timelineEvent.canBeDecrypted() && event is MessageEvent) {
                        log.trace { "getTimelineEvent: try decrypt ${timelineEvent.eventId}" }
                        val decryptedEventContent =
                            try {
                                withTimeout(cfg.decryptionTimeout) {
                                    getTimelineEventDecryptionMutex.withLock(timelineEvent.eventId to roomId) {
                                        val decryptionResult = roomEventEncryptionServices.decrypt(event)
                                        if (decryptionResult != null) {
                                            try {
                                                Result.success(decryptionResult.getOrThrow())
                                            } catch (exception: Exception) {
                                                log.trace { "getTimelineEvent: failed decrypt ${timelineEvent.eventId} (${exception.message})" }
                                                Result.failure(TimelineEventContentError.DecryptionError(exception))
                                            }
                                        } else {
                                            log.trace { "getTimelineEvent: failed decrypt ${timelineEvent.eventId} (algorithm not supported)" }
                                            Result.failure(TimelineEventContentError.DecryptionAlgorithmNotSupported)
                                        }
                                    }
                                }
                            } catch (timeout: TimeoutCancellationException) {
                                log.trace { "getTimelineEvent: failed decrypt ${timelineEvent.eventId} (timeout)" }
                                Result.failure(TimelineEventContentError.DecryptionTimeout)
                            }
                        if (decryptedEventContent.isSuccess) {
                            log.trace { "getTimelineEvent: update decrypted TimelineEvent in store" }
                            roomTimelineStore.update(
                                timelineEvent.eventId,
                                roomId,
                                persistIntoRepository = this@RoomServiceImpl.config.storeTimelineEventContentUnencrypted
                            ) { oldEvent ->
                                // we check here again, because an event could be redacted at the same time
                                if (oldEvent?.canBeDecrypted() == true) timelineEvent.copy(content = decryptedEventContent)
                                else oldEvent
                            }
                        } else {
                            emit(timelineEvent.copy(content = decryptedEventContent))
                        }
                    }
                }
            }.distinctUntilChanged()
    }

    override fun getPreviousTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit,
    ): Flow<TimelineEvent?>? =
        event.previousEventId?.let {
            getTimelineEvent(
                eventId = it,
                roomId = event.roomId,
                config = config,
            )
        }

    override fun getNextTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit,
    ): Flow<TimelineEvent?>? =
        event.nextEventId?.let {
            getTimelineEvent(
                eventId = it,
                roomId = event.roomId,
                config = config,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvent(
        roomId: RoomId,
        config: GetTimelineEventConfig.() -> Unit,
    ): Flow<Flow<TimelineEvent>?> =
        roomStore.get(roomId).transformLatest { room ->
            coroutineScope {
                if (room?.lastEventId != null) emit(
                    getTimelineEvent(
                        roomId = roomId,
                        eventId = room.lastEventId,
                        config = config
                    ).filterNotNull()
                )
                else emit(null)
                delay(INFINITE) // ensure, that the TimelineEvent does not get removed from cache
            }
        }.distinctUntilChanged()

    private interface FollowTimelineResult {
        data class Continue(val timelineEventFlow: Flow<TimelineEvent?>) : FollowTimelineResult
        object Stop : FollowTimelineResult
    }

    override fun getTimelineEvents(
        roomId: RoomId,
        startFrom: EventId,
        direction: Direction,
        config: GetTimelineEventsConfig.() -> Unit,
    ): Flow<Flow<TimelineEvent>> =
        flow {
            val cfg = GetTimelineEventsConfig().apply(config)
            val minSize = cfg.minSize
            val maxSize = cfg.maxSize
            val loopDetectionEventIds = mutableListOf(startFrom)
            fun TimelineEvent.needsFetchGap(): Boolean {
                return gap != null && (gap.hasGapBoth && isLast.not() && isFirst.not()
                        || direction == FORWARDS && gap.hasGapAfter && isLast.not()
                        || direction == BACKWARDS && gap.hasGapBefore && isFirst.not())
            }

            var currentTimelineEventFlow: Flow<TimelineEvent> =
                getTimelineEvent(roomId, startFrom) { apply(cfg) }.filterNotNull()
            emit(currentTimelineEventFlow)
            var size = 1
            while (currentCoroutineContext().isActive) {
                val followTimelineResult: FollowTimelineResult = currentTimelineEventFlow
                    .transform { currentTimelineEvent ->
                        val currentRoomId = currentTimelineEvent.roomId
                        val currentEventId = currentTimelineEvent.eventId

                        // check for room upgrades
                        data class RoomEventIdPair(val eventId: EventId, val roomId: RoomId)

                        val currentTimelineEventContent = currentTimelineEvent.event.content
                        val predecessor: RoomEventIdPair? =
                            if (direction == BACKWARDS && currentTimelineEvent.isFirst && currentTimelineEventContent is CreateEventContent) {
                                currentTimelineEventContent.predecessor
                                    ?.let {
                                        val tombstone = getState<TombstoneEventContent>(it.roomId).first()
                                        if (tombstone != null) RoomEventIdPair(it.eventId, it.roomId)
                                        else {
                                            log.warn { "getTimelineEvents: found predecessor of room, but room does not exist locally" }
                                            null
                                        }
                                    }
                            } else null
                        val successor: RoomEventIdPair? =
                            if (direction == FORWARDS && (currentTimelineEvent.isLast || currentTimelineEventContent is TombstoneEventContent)) {
                                val tombstone =
                                    (currentTimelineEventContent as? TombstoneEventContent)
                                        ?: getState<TombstoneEventContent>(currentTimelineEvent.roomId).first()?.content
                                if (tombstone != null) {
                                    val create =
                                        getState<CreateEventContent>(tombstone.replacementRoom).first()?.idOrNull
                                    if (create != null) RoomEventIdPair(create, tombstone.replacementRoom)
                                    else {
                                        log.warn { "getTimelineEvents: found successor of room, but room does not exist locally" }
                                        null
                                    }
                                } else {
                                    null
                                }
                            } else null

                        // check for break conditions
                        log.trace { "getTimelineEvents: size=$size minSize=$minSize maxSize=$maxSize direction=${direction.name} predecessor=$predecessor successor=$successor currentTimelineEvent=${currentTimelineEvent.eventId}" }
                        if (direction == BACKWARDS && currentTimelineEvent.isFirst && predecessor == null) {
                            log.debug { "getTimelineEvents: reached start of timeline $currentRoomId" }
                            emit(FollowTimelineResult.Stop)
                        }
                        if (minSize != null && size >= minSize
                            && (currentTimelineEvent.needsFetchGap() || (direction == FORWARDS && currentTimelineEvent.isLast))
                        ) {
                            log.debug { "getTimelineEvents: found a gap and complete flow, because minSize reached" }
                            emit(FollowTimelineResult.Stop)
                        }
                        if (maxSize != null && size >= maxSize) {
                            log.debug { "getTimelineEvents: complete flow because maxSize reached" }
                            emit(FollowTimelineResult.Stop)
                        }

                        if (currentTimelineEvent.needsFetchGap()) {
                            log.debug { "getTimelineEvents: found ${currentTimelineEvent.gap} at $currentEventId" }
                            fillTimelineGaps(currentRoomId, currentEventId, cfg.fetchSize)
                        } else {
                            val continueWith = when (direction) {
                                BACKWARDS ->
                                    if (predecessor == null) {
                                        log.trace { "getTimelineEvents: continue with previous event of $currentEventId" }
                                        getPreviousTimelineEvent(
                                            event = currentTimelineEvent,
                                            config = {
                                                apply(cfg)
                                                fetchTimeout = ZERO
                                            }
                                        )
                                    } else {
                                        log.trace { "getTimelineEvents: continue with predecessor ($predecessor) of $currentEventId" }
                                        getTimelineEvent(
                                            eventId = predecessor.eventId,
                                            roomId = predecessor.roomId,
                                            config = { apply(cfg) },
                                        )
                                    }

                                FORWARDS ->
                                    if (successor == null) {
                                        log.trace { "getTimelineEvents: continue with next event of $currentEventId" }
                                        getNextTimelineEvent(
                                            event = currentTimelineEvent,
                                            config = {
                                                apply(cfg)
                                                fetchTimeout = ZERO
                                            }
                                        )
                                    } else {
                                        log.trace { "getTimelineEvents: continue with successor ($successor) of $currentEventId" }
                                        getTimelineEvent(
                                            eventId = successor.eventId,
                                            roomId = successor.roomId,
                                            config = { apply(cfg) },
                                        )
                                    }
                            }
                            if (continueWith != null) {
                                emit(FollowTimelineResult.Continue(continueWith))
                            } else {
                                log.debug { "getTimelineEvents: did not found any event to continue with at $currentEventId, wait for update" }
                            }
                        }
                    }
                    .first()

                when (followTimelineResult) {
                    is FollowTimelineResult.Continue -> {
                        currentTimelineEventFlow = followTimelineResult.timelineEventFlow.filterNotNull()
                    }

                    is FollowTimelineResult.Stop -> break
                }

                // check for loop
                val continueTimelineEventId = currentTimelineEventFlow.first().eventId
                if (loopDetectionEventIds.contains(continueTimelineEventId)) {
                    val message =
                        "Detected a loop in timeline generation. " +
                                "This is a severe misbehavior and must be fixed in Trixnity!!! " +
                                "Event $continueTimelineEventId has already been emitted in this flow (history=$loopDetectionEventIds)."
                    log.error { message } // log even when a consumer don't catch the exception
                    throw IllegalStateException(message)
                } else loopDetectionEventIds.add(continueTimelineEventId)

                log.trace { "getTimelineEvents: continue loop with $continueTimelineEventId" }
                emit(currentTimelineEventFlow)
                size++
            }
        }.buffer(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLastTimelineEvents(
        roomId: RoomId,
        config: GetTimelineEventsConfig.() -> Unit,
    ): Flow<Flow<Flow<TimelineEvent>>?> =
        roomStore.get(roomId)
            .mapLatest { it?.lastEventId }
            .distinctUntilChanged()
            .mapLatest {
                if (it != null) getTimelineEvents(
                    startFrom = it,
                    roomId = roomId,
                    direction = BACKWARDS,
                    config = config,
                )
                else null
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<TimelineEvent> =
        api.sync.subscribeAsFlow(Priority.AFTER_DEFAULT).map { it.syncResponse }
            .buffer(syncResponseBufferSize).flatMapConcat { syncResponse ->
                coroutineScope {
                    val timelineEvents =
                        syncResponse.room?.join?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty() +
                                syncResponse.room?.leave?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty()
                    timelineEvents.map {
                        async {
                            getTimelineEvent(it.roomId, it.id) {
                                this.decryptionTimeout = decryptionTimeout
                            }
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
        transformer: suspend (Flow<TimelineEvent>) -> T,
    ): Timeline<T> =
        TimelineImpl(
            roomId = roomId,
            roomService = this,
            transformer = transformer,
        )

    override fun getTimelineEventRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
    ): Flow<Map<EventId, Flow<TimelineEventRelation?>>?> = roomTimelineStore.getRelations(eventId, roomId, relationType)

    override suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean,
        builder: suspend MessageBuilder.() -> Unit
    ): String {
        val content = MessageBuilder(roomId, this, mediaService, userInfo.userId).build(builder)
        requireNotNull(content) { "you must add some sort of content for sending a message" }
        val transactionId = SecureRandom.nextString(22)
        roomOutboxMessageStore.update(roomId, transactionId) {
            RoomOutboxMessage(
                transactionId = transactionId,
                roomId = roomId,
                content = content,
                createdAt = clock.now(),
                sentAt = null,
                keepMediaInCache = keepMediaInCache,
            )
        }
        return transactionId
    }

    override suspend fun cancelSendMessage(roomId: RoomId, transactionId: String) {
        roomOutboxMessageStore.update(roomId, transactionId) { null }
        log.debug { "removed message with id $transactionId" }
    }

    override suspend fun retrySendMessage(roomId: RoomId, transactionId: String) {
        roomOutboxMessageStore.update(roomId, transactionId) { it?.copy(sendError = null) }
    }

    override fun getAll(): Flow<Map<RoomId, Flow<Room?>>> = roomStore.getAll()

    override fun getById(roomId: RoomId): Flow<Room?> {
        return roomStore.get(roomId)
    }

    override suspend fun forgetRoom(roomId: RoomId) = forgetRoomService(roomId)

    override fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
    ): Flow<C?> {
        return roomAccountDataStore.get(roomId, eventContentClass, key)
            .map { it?.content }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getOutbox(): Flow<List<Flow<RoomOutboxMessage<*>?>>> =
        roomOutboxMessageStore.getAll().flatMapLatest { outbox ->
            if (outbox.isEmpty()) flowOf(emptyList())
            else {
                val innerFlowsWithCreatedAt = outbox.values.map { entry -> entry.map { entry to it?.createdAt } }
                combine(innerFlowsWithCreatedAt) { innerFlowsWithCreatedAtArray ->
                    innerFlowsWithCreatedAtArray
                        .mapNotNull { entry -> entry.second?.let { entry.first to entry.second } }
                        .sortedBy { it.second }
                        .map { it.first }
                }
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getOutbox(roomId: RoomId): Flow<List<Flow<RoomOutboxMessage<*>?>>> =
        roomOutboxMessageStore.getAll().flatMapLatest { outbox ->
            val roomOutbox = outbox.filterKeys { it.roomId == roomId }.values
            if (roomOutbox.isEmpty()) flowOf(emptyList())
            else {
                val innerFlowsWithCreatedAt = roomOutbox.map { entry -> entry.map { entry to it?.createdAt } }
                combine(innerFlowsWithCreatedAt) { innerFlowsWithCreatedAtArray ->
                    innerFlowsWithCreatedAtArray
                        .mapNotNull { entry -> entry.second?.let { entry.first to entry.second } }
                        .sortedBy { it.second }
                        .map { it.first }
                }
            }
        }.distinctUntilChanged()

    override fun getOutbox(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        roomOutboxMessageStore.get(roomId, transactionId)

    override fun <C : StateEventContent> getState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String,
    ): Flow<StateBaseEvent<C>?> {
        return roomStateStore.getByStateKey(roomId, eventContentClass, stateKey)
    }

    override fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<StateBaseEvent<C>?>>> {
        return roomStateStore.get(roomId, eventContentClass)
    }
}