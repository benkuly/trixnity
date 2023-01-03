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

typealias SimpleTimeline = Timeline<Flow<TimelineEvent>>

/**
 * This is an abstraction for a timeline. Call [init] first!
 */
interface Timeline<T> {
    /**
     * The current state of the timeline.
     */
    val state: Flow<TimelineState<T>>

    /**
     * Initialize the timeline with the start event.
     *
     * Consider wrapping this method call in a timeout, since it might fetch the start event from the server if it is not found locally.
     *
     * The timeline can be initialized multiple times from different starting events.
     * If doing so, it must be ensured, that there is no running call to [loadBefore] or [loadAfter].
     * Otherwise [init] will suspend until [loadBefore] or [loadAfter] are finished.
     *
     * @param startFrom The event id to try start timeline generation from. Default: last room event id
     */
    suspend fun init(startFrom: EventId): TimelineStateChange<T>

    /**
     * Load new events before the oldest event. This may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     */
    suspend fun loadBefore(): TimelineStateChange<T>

    /**
     * Load new events after the newest event. This may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     */
    suspend fun loadAfter(): TimelineStateChange<T>
}

data class TimelineState<T>(
    /**
     * Elements sorted with higher indexes being more recent.
     */
    val elements: List<T> = listOf(),

    /**
     * Lower bound of loaded events in this timeline.
     */
    val lastLoadedEventIdBefore: EventId? = null,

    /**
     * Upper bound of loaded events in this timeline.
     */
    val lastLoadedEventIdAfter: EventId? = null,

    /**
     * True when timeline initialization has been finished.
     */
    val isInitialized: Boolean = false,

    /**
     * True while events are loaded before.
     */
    val isLoadingBefore: Boolean = false,

    /**
     * True while events are loaded after.
     */
    val isLoadingAfter: Boolean = false,

    /**
     * Is true until start of timeline is reached.
     */
    val canLoadBefore: Boolean = false,

    /**
     * Is true until last known [TimelineEvent] is reached.
     */
    val canLoadAfter: Boolean = false,
)

data class TimelineStateChange<T>(
    val elementsBeforeChange: List<T> = listOf(),
    val elementsAfterChange: List<T> = listOf(),
    val newElements: List<T> = listOf(),
)

/**
 * An implementation for some restrictions required by [Timeline].
 *
 * Implementing this may be useful for tests (e.g. a TimelineMock).
 */
abstract class TimelineBase<T>(
    val transformer: suspend (Flow<TimelineEvent>) -> T,
) : Timeline<T> {
    private data class InternalState<T>(
        val elements: List<T> = listOf(),
        val lastLoadedEventBefore: Flow<TimelineEvent>? = null,
        val lastLoadedEventAfter: Flow<TimelineEvent>? = null,
        val isInitialized: Boolean = false,
        val isLoadingBefore: Boolean = false,
        val isLoadingAfter: Boolean = false,
    )

    private val internalState = MutableStateFlow(InternalState<T>())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<TimelineState<T>> =
        internalState.flatMapLatest { internalState ->
            combine(
                (internalState.lastLoadedEventBefore ?: flowOf(null)).map { it?.isFirst != true },
                (internalState.lastLoadedEventAfter ?: flowOf(null)).map { it?.isLast != true }
            ) { canLoadBefore, canLoadAfter ->
                TimelineState(
                    elements = internalState.elements,
                    lastLoadedEventIdBefore = internalState.lastLoadedEventBefore?.first()?.eventId,
                    lastLoadedEventIdAfter = internalState.lastLoadedEventAfter?.first()?.eventId,
                    isInitialized = internalState.isInitialized,
                    isLoadingBefore = internalState.isLoadingBefore,
                    isLoadingAfter = internalState.isLoadingAfter,
                    canLoadBefore = canLoadBefore,
                    canLoadAfter = canLoadAfter,
                )
            }
        }.distinctUntilChanged()

    private val loadBeforeMutex = Mutex()
    private val loadAfterMutex = Mutex()

    protected abstract suspend fun internalInit(startFrom: EventId): List<Flow<TimelineEvent>>
    protected abstract suspend fun internalLoadBefore(startFrom: EventId): List<Flow<TimelineEvent>>
    protected abstract suspend fun internalLoadAfter(startFrom: EventId): List<Flow<TimelineEvent>>

    private suspend fun List<Flow<TimelineEvent>>.transformToElements() = map { events -> transformer(events) }

    override suspend fun init(startFrom: EventId): TimelineStateChange<T> = coroutineScope {
        loadBeforeMutex.withLock {
            loadAfterMutex.withLock {
                internalState.update { it.copy(isInitialized = false) }
                val newEvents = internalInit(startFrom)
                val newElements = newEvents.transformToElements()
                lateinit var elementsBeforeChange: List<T>
                internalState.update {
                    elementsBeforeChange = it.elements
                    it.copy(
                        elements = newElements,
                        lastLoadedEventBefore = newEvents.firstOrNull(),
                        lastLoadedEventAfter = newEvents.lastOrNull(),
                        isInitialized = true,
                    )
                }
                TimelineStateChange(
                    elementsBeforeChange = elementsBeforeChange,
                    elementsAfterChange = newElements,
                    newElements = newElements
                )
            }
        }
    }

    override suspend fun loadBefore(): TimelineStateChange<T> = coroutineScope {
        internalState.first { it.isInitialized }
        loadBeforeMutex.withLock {
            val startFrom = internalState.value.lastLoadedEventBefore?.first()?.eventId
                ?: throw IllegalStateException("Timeline not initialized")
            coroutineContext.job.invokeOnCompletion { error ->
                if (error != null) internalState.update { it.copy(isLoadingBefore = false) }
            }
            internalState.update { it.copy(isLoadingBefore = true) }
            val newEvents = internalLoadBefore(startFrom)
            val newElements = newEvents.transformToElements()
            lateinit var elementsBeforeChange: List<T>
            lateinit var elementsAfterChange: List<T>
            internalState.update {
                elementsBeforeChange = it.elements
                elementsAfterChange = newElements + it.elements
                it.copy(
                    elements = elementsAfterChange,
                    lastLoadedEventBefore = newEvents.firstOrNull() ?: it.lastLoadedEventBefore,
                    isLoadingBefore = false
                )
            }
            TimelineStateChange(
                elementsBeforeChange = elementsBeforeChange,
                elementsAfterChange = elementsAfterChange,
                newElements = newElements
            )
        }
    }

    override suspend fun loadAfter(): TimelineStateChange<T> = coroutineScope {
        internalState.first { it.isInitialized }
        loadAfterMutex.withLock {
            val startFrom = internalState.value.lastLoadedEventAfter?.first()?.eventId
                ?: throw IllegalStateException("Timeline not initialized")
            coroutineContext.job.invokeOnCompletion { error ->
                if (error != null) internalState.update { it.copy(isLoadingAfter = false) }
            }
            internalState.update { it.copy(isLoadingAfter = true) }
            val newEvents = internalLoadAfter(startFrom)
            val newElements = newEvents.transformToElements()
            lateinit var elementsBeforeChange: List<T>
            lateinit var elementsAfterChange: List<T>
            internalState.update {
                elementsBeforeChange = it.elements
                elementsAfterChange = it.elements + newElements
                it.copy(
                    elements = elementsAfterChange,
                    lastLoadedEventAfter = newEvents.lastOrNull() ?: it.lastLoadedEventAfter,
                    isLoadingAfter = false,
                )
            }
            TimelineStateChange(
                elementsBeforeChange = elementsBeforeChange,
                elementsAfterChange = elementsAfterChange,
                newElements = newElements
            )
        }
    }
}

class TimelineImpl<T>(
    private val roomId: RoomId,
    private val decryptionTimeout: Duration = INFINITE,
    private val fetchTimeout: Duration = 1.minutes,
    private val limitPerFetch: Long = 20,
    private val loadingSize: Long = limitPerFetch,
    private val roomService: RoomService,
    transformer: suspend (Flow<TimelineEvent>) -> T,
) : TimelineBase<T>(transformer) {
    override suspend fun internalInit(startFrom: EventId): List<Flow<TimelineEvent>> = coroutineScope {
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
        log.debug { "finished init timeline" }
        newEvents
    }

    override suspend fun internalLoadBefore(startFrom: EventId): List<Flow<TimelineEvent>> {
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
        log.debug { "finished load before $startFrom" }
        return newEvents
    }

    override suspend fun internalLoadAfter(startFrom: EventId): List<Flow<TimelineEvent>> {
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
        log.debug { "finished load after $startFrom" }
        return newEvents
    }
}