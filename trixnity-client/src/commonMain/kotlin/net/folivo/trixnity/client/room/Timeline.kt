package net.folivo.trixnity.client.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.isFirst
import net.folivo.trixnity.client.store.isLast
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger { }

/**
 * This is an abstraction for a timeline. Call [init] first!
 */
interface Timeline {
    /**
     * [TimelineEvent]s sorted with higher indexes being more recent.
     */
    val events: Flow<List<Flow<TimelineEvent>>>

    /**
     * True when timeline initialization has been finished.
     */
    val isInitialized: Flow<Boolean>

    /**
     * True while events are loaded before.
     */
    val isLoadingBefore: Flow<Boolean>

    /**
     * True while events are loaded after.
     */
    val isLoadingAfter: Flow<Boolean>

    /**
     * Is true until start of timeline is reached.
     */
    val canLoadBefore: Flow<Boolean>

    /**
     * Is true until last known [TimelineEvent] is reached.
     */
    val canLoadAfter: Flow<Boolean>

    /**
     * Lower bound of loaded events in this timeline.
     */
    val lastLoadedEventIdBefore: Flow<EventId?>

    /**
     * Upper bound of loaded events in this timeline.
     */
    val lastLoadedEventIdAfter: Flow<EventId?>

    /**
     * Initialize the timeline with the start event.
     *
     * Consider wrapping this method call in a timeout, since it might fetch the start event from the server if it is not found locally.
     *
     * @param startFrom The event id to try start timeline generation from. Default: last room event id
     * @return The initial list of events loaded into the timeline.
     */
    suspend fun init(startFrom: EventId): List<Flow<TimelineEvent>>

    /**
     * Load new events before the oldest event. This may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     *
     * @return The list of new events loaded into the timeline.
     */
    suspend fun loadBefore(): List<Flow<TimelineEvent>>

    /**
     * Load new events after the newest event. This may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     *
     * @return The list of new events loaded into the timeline.
     */
    suspend fun loadAfter(): List<Flow<TimelineEvent>>
}

class TimelineImpl(
    private val roomId: RoomId,
    private val decryptionTimeout: Duration = INFINITE,
    private val fetchTimeout: Duration = 1.minutes,
    private val limitPerFetch: Long = 20,
    private val loadingSize: Long = limitPerFetch,
    private val roomService: RoomService,
) : Timeline {
    private data class State(
        val events: List<Flow<TimelineEvent>> = listOf(),
        val lastLoadedEventIdBefore: EventId? = null,
        val lastLoadedEventIdAfter: EventId? = null,
        val isInitialized: Boolean = false,
    )

    private val state = MutableStateFlow(State())

    override val events: Flow<List<Flow<TimelineEvent>>> = state.map { it.events }
    override val lastLoadedEventIdBefore: Flow<EventId?> = state.map { it.lastLoadedEventIdBefore }
    override val lastLoadedEventIdAfter: Flow<EventId?> = state.map { it.lastLoadedEventIdAfter }
    private val _isLoadingBefore = MutableStateFlow(false)
    override val isLoadingBefore: StateFlow<Boolean> = _isLoadingBefore.asStateFlow()
    private val _isLoadingAfter = MutableStateFlow(false)
    override val isLoadingAfter: StateFlow<Boolean> = _isLoadingAfter.asStateFlow()
    override val isInitialized: Flow<Boolean> = state.map { it.isInitialized }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val canLoadBefore: Flow<Boolean> =
        events.flatMapLatest { it.lastOrNull() ?: flowOf(null) }.map { it?.isFirst != true }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val canLoadAfter: Flow<Boolean> =
        events.flatMapLatest { it.firstOrNull() ?: flowOf(null) }.map { it?.isLast != true }

    private val loadBeforeMutex = Mutex()
    private val loadAfterMutex = Mutex()

    override suspend fun init(startFrom: EventId): List<Flow<TimelineEvent>> = coroutineScope {
        loadBeforeMutex.withLock {
            loadAfterMutex.withLock {
                log.debug { "init timeline" }
                val startFromEvent = roomService.getTimelineEvent(
                    eventId = startFrom,
                    roomId = roomId,
                    decryptionTimeout = decryptionTimeout,
                    fetchTimeout = INFINITE,
                    limitPerFetch = 100
                ).filterNotNull()
                    .also { it.first() } // wait until it exists in store
                val eventsBefore = async {
                    log.debug { "load before $startFrom" }
                    roomService.getTimelineEvents(
                        startFrom = startFrom,
                        roomId = roomId,
                        direction = GetEvents.Direction.BACKWARDS,
                        decryptionTimeout = decryptionTimeout,
                        fetchTimeout = fetchTimeout,
                        limitPerFetch = limitPerFetch,
                        minSize = 1,
                        maxSize = loadingSize / 2,
                    ).drop(1).toList().reversed()
                        .also { log.debug { "finished load before $startFrom" } }
                }
                val eventsAfter = async {
                    log.debug { "load after $startFrom" }
                    roomService.getTimelineEvents(
                        startFrom = startFrom,
                        roomId = roomId,
                        direction = GetEvents.Direction.FORWARDS,
                        decryptionTimeout = decryptionTimeout,
                        fetchTimeout = fetchTimeout,
                        limitPerFetch = limitPerFetch,
                        minSize = 1,
                        maxSize = loadingSize / 2,
                    ).drop(1).toList()
                        .also { log.debug { "finished load after $startFrom" } }
                }
                val newEvents = eventsBefore.await() + startFromEvent + eventsAfter.await()
                state.update {
                    it.copy(
                        events = newEvents,
                        lastLoadedEventIdBefore = newEvents.firstOrNull()?.first()?.eventId ?: startFrom,
                        lastLoadedEventIdAfter = newEvents.lastOrNull()?.first()?.eventId ?: startFrom,
                        isInitialized = true
                    )
                }
                log.debug { "finished init timeline" }
                newEvents
            }
        }
    }

    override suspend fun loadBefore(): List<Flow<TimelineEvent>> = coroutineScope {
        isInitialized.first { it }
        loadBeforeMutex.withLock {
            val startFrom = state.value.lastLoadedEventIdBefore
                ?: throw IllegalStateException("Timeline not initialized")
            log.debug { "load before $startFrom" }
            coroutineContext.job.invokeOnCompletion { _isLoadingBefore.value = false }
            _isLoadingBefore.value = true
            val newEvents = roomService.getTimelineEvents(
                startFrom = startFrom,
                roomId = roomId,
                direction = GetEvents.Direction.BACKWARDS,
                decryptionTimeout = decryptionTimeout,
                fetchTimeout = fetchTimeout,
                limitPerFetch = limitPerFetch,
                minSize = 2,
                maxSize = loadingSize,
            ).drop(1).toList().reversed().map { it.filterNotNull() }

            if (newEvents.isNotEmpty())
                state.update {
                    it.copy(
                        events = newEvents + it.events,
                        lastLoadedEventIdBefore = newEvents.first().first().eventId
                    )
                }
            log.debug { "finished load before $startFrom" }
            newEvents
        }
    }

    override suspend fun loadAfter(): List<Flow<TimelineEvent>> = coroutineScope {
        isInitialized.first { it }
        loadAfterMutex.withLock {
            val startFrom = state.value.lastLoadedEventIdAfter
                ?: throw IllegalStateException("Timeline not initialized")
            log.debug { "load after $startFrom" }
            coroutineContext.job.invokeOnCompletion { _isLoadingAfter.value = false }
            _isLoadingAfter.value = true
            val newEvents = roomService.getTimelineEvents(
                startFrom = startFrom,
                roomId = roomId,
                direction = GetEvents.Direction.FORWARDS,
                decryptionTimeout = decryptionTimeout,
                fetchTimeout = fetchTimeout,
                limitPerFetch = limitPerFetch,
                minSize = 2,
                maxSize = loadingSize,
            ).drop(1).toList().map { it.filterNotNull() }

            if (newEvents.isNotEmpty())
                state.update {
                    it.copy(
                        events = it.events + newEvents,
                        lastLoadedEventIdAfter = newEvents.last().first().eventId
                    )
                }
            log.debug { "finished load after $startFrom" }
            newEvents
        }
    }
}