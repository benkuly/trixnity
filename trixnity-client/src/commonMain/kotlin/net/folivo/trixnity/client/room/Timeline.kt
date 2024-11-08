package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.INFINITE

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
     * @param startFrom The event id to try start timeline generation from.
     * @param configStart The config for getting the [startFrom].
     * @param configBefore The config for getting [TimelineEvent]s before [startFrom].
     * @param configAfter The config for getting [TimelineEvent]s after [startFrom].
     */
    suspend fun init(
        startFrom: EventId,
        configStart: GetTimelineEventConfig.() -> Unit = {},
        configBefore: GetTimelineEventsConfig.() -> Unit = {},
        configAfter: GetTimelineEventsConfig.() -> Unit = {},
    ): TimelineStateChange<T>

    /**
     * Load new events before the oldest event. With default config this may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     *
     * @param config The config for getting [TimelineEvent]s.
     */
    suspend fun loadBefore(config: GetTimelineEventsConfig.() -> Unit = {}): TimelineStateChange<T>

    /**
     * Load new events after the newest event. With default config this may suspend until at least one event can be loaded.
     *
     * This will also suspend until [init] is finished.
     *
     * @param config The config for getting [TimelineEvent]s.
     */
    suspend fun loadAfter(config: GetTimelineEventsConfig.() -> Unit = {}): TimelineStateChange<T>

    /**
     * Drop all events before a given [eventId].
     *
     * Be aware that [roomId] can differ from the initial roomId because of room upgrades.
     */
    suspend fun dropBefore(roomId: RoomId, eventId: EventId): TimelineStateChange<T>

    /**
     * Drop all events after a given [eventId].
     *
     * Be aware that [roomId] can differ from the initial roomId because of room upgrades.
     */
    suspend fun dropAfter(roomId: RoomId, eventId: EventId): TimelineStateChange<T>
}

data class TimelineState<T>(
    /**
     * Elements sorted with higher indexes being more recent.
     */
    val elements: List<T> = listOf(),

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
    val removedElements: List<T> = listOf(),
)

/**
 * An implementation for some restrictions required by [Timeline].
 *
 * Implementing this may be useful for tests (e.g. a TimelineMock).
 *
 */
abstract class TimelineBase<T>(
    val maxSize: Int = 100,
    val transformer: suspend (Flow<TimelineEvent>) -> T,
) : Timeline<T> {
    protected abstract suspend fun internalInit(
        startFrom: EventId,
        configStart: GetTimelineEventConfig.() -> Unit = {},
        configBefore: GetTimelineEventsConfig.() -> Unit,
        configAfter: GetTimelineEventsConfig.() -> Unit,
    ): List<Flow<TimelineEvent>>

    protected abstract suspend fun internalLoadBefore(
        startFrom: EventId,
        config: GetTimelineEventsConfig.() -> Unit,
    ): List<Flow<TimelineEvent>>

    protected abstract suspend fun internalLoadAfter(
        startFrom: EventId,
        config: GetTimelineEventsConfig.() -> Unit,
    ): List<Flow<TimelineEvent>>

    protected abstract suspend fun Flow<TimelineEvent>.canLoadBefore(): Flow<Boolean>
    protected abstract suspend fun Flow<TimelineEvent>.canLoadAfter(): Flow<Boolean>


    private data class EventWithMeta(
        val eventId: EventId,
        val roomId: RoomId,
        val event: Flow<TimelineEvent>,
    )

    private data class InternalState<T>(
        val events: List<EventWithMeta> = listOf(),
        val elements: List<T> = listOf(),
        val isInitialized: Boolean = false,
        val isLoadingBefore: Boolean = false,
        val isLoadingAfter: Boolean = false,
    ) {
        val lastLoadedEventBefore: Flow<TimelineEvent>?
            get() = events.firstOrNull()?.event
        val lastLoadedEventAfter: Flow<TimelineEvent>?
            get() = events.lastOrNull()?.event
    }

    private val internalState = MutableStateFlow(InternalState<T>())

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<TimelineState<T>> =
        internalState.flatMapLatest { internalState ->
            combine(
                internalState.lastLoadedEventBefore?.canLoadBefore() ?: flowOf(true),
                internalState.lastLoadedEventAfter?.canLoadAfter() ?: flowOf(true)
            ) { canLoadBefore, canLoadAfter ->
                TimelineState(
                    elements = internalState.elements,
                    isInitialized = internalState.isInitialized,
                    isLoadingBefore = internalState.isLoadingBefore,
                    isLoadingAfter = internalState.isLoadingAfter,
                    canLoadBefore = canLoadBefore,
                    canLoadAfter = canLoadAfter,
                )
            }
        }.distinctUntilChanged()

    private val editSemaphore = Semaphore(2)
    private val loadBeforeMutex = Mutex()
    private val loadAfterMutex = Mutex()

    private suspend fun List<Flow<TimelineEvent>>.transformToElements() = map { events -> transformer(events) }

    override suspend fun init(
        startFrom: EventId,
        configStart: GetTimelineEventConfig.() -> Unit,
        configBefore: GetTimelineEventsConfig.() -> Unit,
        configAfter: GetTimelineEventsConfig.() -> Unit,
    ): TimelineStateChange<T> = coroutineScope {
        editSemaphore.withPermit(2) {
            internalState.update { it.copy(isInitialized = false) }
            val newEvents = internalInit(
                startFrom = startFrom,
                configStart = {
                    fetchTimeout = INFINITE
                    fetchSize = 100
                    configStart()
                },
                configBefore = {
                    minSize = 1
                    maxSize = fetchSize / 2
                    configBefore()
                },
                configAfter = {
                    minSize = 1
                    maxSize = fetchSize / 2
                    configAfter()
                }
            )
            val newEventsWithMeta = newEvents.transformToEventsWithMeta()
            val newElements = newEvents.transformToElements()
            lateinit var elementsBeforeChange: List<T>
            internalState.update {
                elementsBeforeChange = it.elements
                it.copy(
                    events = newEventsWithMeta,
                    elements = newElements,
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

    override suspend fun loadBefore(config: GetTimelineEventsConfig.() -> Unit): TimelineStateChange<T> =
        coroutineScope {
            internalState.first { it.isInitialized }
            loadBeforeMutex.withLock {
                editSemaphore.withPermit(1) {
                    val startFrom = internalState.value.lastLoadedEventBefore?.first()?.eventId
                        ?: throw IllegalStateException("Timeline not initialized")
                    coroutineContext.job.invokeOnCompletion { error ->
                        if (error != null) internalState.update { it.copy(isLoadingBefore = false) }
                    }
                    internalState.update { it.copy(isLoadingBefore = true) }
                    val newEvents = internalLoadBefore(startFrom) {
                        minSize = 2
                        maxSize = fetchSize
                        config()
                    }
                    val newEventsWithMeta = newEvents.transformToEventsWithMeta()
                    val newElements = newEvents.transformToElements()
                    lateinit var elementsBeforeChange: List<T>
                    lateinit var elementsAfterChange: List<T>
                    internalState.update {
                        elementsBeforeChange = it.elements
                        elementsAfterChange = newElements + it.elements
                        val eventsAfterChange = newEventsWithMeta + it.events
                        it.copy(
                            events = eventsAfterChange,
                            elements = elementsAfterChange,
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
        }

    override suspend fun loadAfter(config: GetTimelineEventsConfig.() -> Unit): TimelineStateChange<T> =
        coroutineScope {
            internalState.first { it.isInitialized }
            loadAfterMutex.withLock {
                editSemaphore.withPermit(1) {
                    val startFrom = internalState.value.lastLoadedEventAfter?.first()?.eventId
                        ?: throw IllegalStateException("Timeline not initialized")
                    coroutineContext.job.invokeOnCompletion { error ->
                        if (error != null) internalState.update { it.copy(isLoadingAfter = false) }
                    }
                    internalState.update { it.copy(isLoadingAfter = true) }
                    val newEvents = internalLoadAfter(startFrom) {
                        minSize = 2
                        maxSize = fetchSize
                        config()
                    }
                    val newEventsWithMeta = newEvents.transformToEventsWithMeta()
                    val newElements = newEvents.transformToElements()
                    lateinit var elementsBeforeChange: List<T>
                    lateinit var elementsAfterChange: List<T>
                    internalState.update {
                        elementsBeforeChange = it.elements
                        elementsAfterChange = it.elements + newElements
                        val eventsAfterChange = it.events + newEventsWithMeta
                        it.copy(
                            events = eventsAfterChange,
                            elements = elementsAfterChange,
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

    override suspend fun dropBefore(roomId: RoomId, eventId: EventId): TimelineStateChange<T> =
        editSemaphore.withPermit(2) {
            lateinit var elementsBeforeChange: List<T>
            lateinit var elementsAfterChange: List<T>
            lateinit var removedElements: List<T>
            internalState.update {
                val index = it.events.indexOfFirst { it.eventId == eventId && it.roomId == roomId }
                val dropCount =
                    if (index >= 0) index
                    else {
                        log.warn { "could not found event" }
                        0
                    }
                if (dropCount == 0) log.warn { "dropped nothing" }
                else log.debug { "dropped $dropCount before" }
                elementsBeforeChange = it.elements
                elementsAfterChange = it.elements.drop(dropCount)
                removedElements = it.elements.take(dropCount)
                val eventsAfterChange = it.events.drop(dropCount)
                it.copy(
                    events = eventsAfterChange,
                    elements = elementsAfterChange,
                    isLoadingAfter = false,
                )
            }
            TimelineStateChange(
                elementsBeforeChange = elementsBeforeChange,
                elementsAfterChange = elementsAfterChange,
                removedElements = removedElements,
            )
        }

    override suspend fun dropAfter(roomId: RoomId, eventId: EventId): TimelineStateChange<T> =
        editSemaphore.withPermit(2) {
            lateinit var elementsBeforeChange: List<T>
            lateinit var elementsAfterChange: List<T>
            lateinit var removedElements: List<T>
            internalState.update {
                val index = it.events.indexOfFirst { it.eventId == eventId && it.roomId == roomId }
                val dropCount =
                    if (index >= 0) it.events.size - index - 1
                    else {
                        log.warn { "could not found event" }
                        0
                    }
                if (dropCount == 0) log.warn { "dropped nothing" }
                else log.debug { "dropped $dropCount after" }
                elementsBeforeChange = it.elements
                elementsAfterChange = it.elements.dropLast(dropCount)
                removedElements = it.elements.takeLast(dropCount)
                val eventsAfterChange = it.events.dropLast(dropCount)
                it.copy(
                    events = eventsAfterChange,
                    elements = elementsAfterChange,
                    isLoadingAfter = false,
                )
            }
            TimelineStateChange(
                elementsBeforeChange = elementsBeforeChange,
                elementsAfterChange = elementsAfterChange,
                removedElements = removedElements,
            )
        }

    private suspend fun List<Flow<TimelineEvent>>.transformToEventsWithMeta(): List<EventWithMeta> =
        map {
            val event = it.first()
            EventWithMeta(event.eventId, event.roomId, it)
        }

    private val acquireMutex = Mutex()

    @OptIn(ExperimentalContracts::class)
    private suspend inline fun <T> Semaphore.withPermit(permits: Int, action: () -> T): T {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        var acquiredPermits = 0
        acquireMutex.withLock {
            repeat(permits) {
                acquire()
                acquiredPermits++
            }
        }
        return try {
            action()
        } finally {
            repeat(acquiredPermits) {
                release()
            }
        }
    }
}

class TimelineImpl<T>(
    private val roomId: RoomId,
    maxSize: Int = 100,
    private val roomService: RoomService,
    transformer: suspend (Flow<TimelineEvent>) -> T,
) : TimelineBase<T>(maxSize, transformer) {
    override suspend fun internalInit(
        startFrom: EventId,
        configStart: GetTimelineEventConfig.() -> Unit,
        configBefore: GetTimelineEventsConfig.() -> Unit,
        configAfter: GetTimelineEventsConfig.() -> Unit
    ): List<Flow<TimelineEvent>> = coroutineScope {
        log.debug { "init timeline" }
        val startFromEvent = roomService.getTimelineEvent(
            eventId = startFrom,
            roomId = roomId,
            config = configStart
        ).filterNotNull()
            .also { it.first() } // wait until it exists in store
        val eventsBefore = async {
            log.debug { "load before $startFrom" }
            roomService.getTimelineEvents(
                startFrom = startFrom,
                roomId = roomId,
                direction = GetEvents.Direction.BACKWARDS,
                config = configBefore,
            ).drop(1).toList().reversed()
                .also { log.debug { "finished load before $startFrom" } }
        }
        val eventsAfter = async {
            log.debug { "load after $startFrom" }
            roomService.getTimelineEvents(
                startFrom = startFrom,
                roomId = roomId,
                direction = GetEvents.Direction.FORWARDS,
                config = configAfter,
            ).drop(1).toList()
                .also { log.debug { "finished load after $startFrom" } }
        }
        val newEvents = eventsBefore.await() + startFromEvent + eventsAfter.await()
        log.debug { "finished init timeline" }
        newEvents
    }

    override suspend fun internalLoadBefore(
        startFrom: EventId,
        config: GetTimelineEventsConfig.() -> Unit
    ): List<Flow<TimelineEvent>> {
        log.debug { "load before $startFrom" }
        val newEvents = roomService.getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.BACKWARDS,
            config = config,
        ).drop(1).toList().reversed().map { it.filterNotNull() }
        log.debug { "finished load before $startFrom" }
        return newEvents
    }

    override suspend fun internalLoadAfter(
        startFrom: EventId,
        config: GetTimelineEventsConfig.() -> Unit
    ): List<Flow<TimelineEvent>> {
        log.debug { "load after $startFrom" }
        val newEvents = roomService.getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.FORWARDS,
            config = config,
        ).drop(1).toList().map { it.filterNotNull() }
        log.debug { "finished load after $startFrom" }
        return newEvents
    }

    override suspend fun Flow<TimelineEvent>.canLoadBefore(): Flow<Boolean> = map { timelineEvent ->
        val createEventContent = timelineEvent.event.content as? CreateEventContent
        timelineEvent.isFirst.not() || createEventContent?.predecessor != null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun Flow<TimelineEvent>.canLoadAfter(): Flow<Boolean> = flatMapLatest { timelineEvent ->
        roomService.getState<TombstoneEventContent>(timelineEvent.roomId).map { tombstone ->
            timelineEvent.isLast.not() || tombstone != null
        }
    }
}