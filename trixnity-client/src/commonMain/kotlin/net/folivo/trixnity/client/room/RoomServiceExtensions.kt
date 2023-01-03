package net.folivo.trixnity.client.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * @see RoomService.getTimeline
 */
fun RoomService.getTimeline(
    roomId: RoomId,
    decryptionTimeout: Duration,
    fetchTimeout: Duration,
    limitPerFetch: Long,
    loadingSize: Long,
): SimpleTimeline =
    getTimeline(
        roomId = roomId,
        decryptionTimeout = decryptionTimeout,
        fetchTimeout = fetchTimeout,
        limitPerFetch = limitPerFetch,
        loadingSize = loadingSize,
    ) { it }

inline fun <reified C : RoomAccountDataEventContent> RoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
): Flow<C?> {
    return getAccountData(roomId, C::class, key)
}

inline fun <reified C : StateEventContent> RoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
): Flow<Event<C>?> {
    return getState(roomId, stateKey, C::class)
}

inline fun <reified C : StateEventContent> RoomService.getAllState(
    roomId: RoomId,
): Flow<Map<String, Event<C>?>?> {
    return getAllState(roomId, C::class)
}

/**
 * This collects all rooms, so when one room changes, a new room set gets emitted.
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is debounced by default.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun StateFlow<Map<RoomId, StateFlow<Room?>>>.flatten(debounceTimeout: Duration = 200.milliseconds): Flow<Set<Room>> =
    debounce(debounceTimeout)
        .flatMapLatest {
            if (it.isEmpty()) flowOf(arrayOf())
            else combine(it.values) { transform -> transform }
        }
        .mapLatest { it.filterNotNull().toSet() }

/**
 * Converts a flow of timeline events into a flow of list of timeline events limited by [maxSize].
 *
 * ```
 * Input: (E) -> (E) -> (E) -> delay e. g. due to fetching new events -> (E)
 * Output: ([EEE]) -> delay -> ([EEEE])
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toList")
fun Flow<Flow<TimelineEvent>>.toFlowList(
    maxSize: StateFlow<Int>,
    minSize: MutableStateFlow<Int> = MutableStateFlow(0)
): Flow<List<Flow<TimelineEvent>>> =
    maxSize.flatMapLatest { listSize ->
        take(listSize)
            .scan<Flow<TimelineEvent>, List<Flow<TimelineEvent>>>(listOf()) { old, new -> old + new }
            .filter { it.size >= if (maxSize.value < minSize.value) maxSize.value else minSize.value }
            .onEach { minSize.value = it.size }
            .distinctUntilChanged()
    }

/**
 * Converts a flow of flow of timeline events into a flow of list of timeline events limited by [maxSize].
 *
 * ```
 * Input: (E) -> (E) -> (E) -> delay e. g. due to fetching new events -> (E)
 * Output: ([EEE]) -> delay -> ([EEEE])
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toListFromLatest")
fun Flow<Flow<Flow<TimelineEvent>>?>.toFlowList(
    maxSize: StateFlow<Int>,
    minSize: MutableStateFlow<Int> = MutableStateFlow(0)
): Flow<List<Flow<TimelineEvent>>> =
    flatMapLatest {
        it?.toFlowList(maxSize, minSize) ?: flowOf(listOf())
    }

/**
 * Returns all timeline events around a starting event sorted with higher indexes being more recent.
 *
 * The size of the returned list can be expanded in 2 directions: before and after the start element.
 *
 * @param startFrom the start event id
 * @param maxSizeBefore how many events to possibly get before the start event
 * @param maxSizeAfter how many events to possibly get after the start event
 *
 */
fun RoomService.getTimelineEventsAround(
    startFrom: EventId,
    roomId: RoomId,
    decryptionTimeout: Duration = Duration.INFINITE,
    fetchTimeout: Duration = 1.minutes,
    limitPerFetch: Long = 20,
    maxSizeBefore: StateFlow<Int>,
    maxSizeAfter: StateFlow<Int>,
): Flow<List<Flow<TimelineEvent>>> =
    channelFlow {
        val startEvent =
            getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch).filterNotNull()
        startEvent.first()
        combine(
            getTimelineEvents(
                startFrom, roomId,
                GetEvents.Direction.BACKWARDS, decryptionTimeout, fetchTimeout, limitPerFetch
            )
                .drop(1)
                .toFlowList(maxSizeBefore)
                .map { it.reversed() },
            getTimelineEvents(
                startFrom, roomId,
                GetEvents.Direction.FORWARDS, decryptionTimeout, fetchTimeout, limitPerFetch
            )
                .drop(1)
                .toFlowList(maxSizeAfter),
        ) { beforeElements, afterElements ->
            beforeElements + startEvent + afterElements
        }.collectLatest { send(it) }
    }.buffer(0)

/**
 * Returns all timeline events around a starting event.
 *
 * @see [RoomService.getTimelineEvents]
 *
 */
suspend fun RoomService.getTimelineEventsAround(
    startFrom: EventId,
    roomId: RoomId,
    decryptionTimeout: Duration = Duration.INFINITE,
    fetchTimeout: Duration = 1.minutes,
    limitPerFetch: Long = 20,
    minSizeBefore: Long? = 2,
    minSizeAfter: Long? = minSizeBefore,
    maxSizeBefore: Long = 10,
    maxSizeAfter: Long = maxSizeBefore,
): List<Flow<TimelineEvent>> = coroutineScope {
    val startEvent = getTimelineEvent(startFrom, roomId, decryptionTimeout, fetchTimeout, limitPerFetch).filterNotNull()
    val eventsBefore = async {
        getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.BACKWARDS,
            decryptionTimeout = decryptionTimeout,
            fetchTimeout = fetchTimeout,
            limitPerFetch = limitPerFetch,
            minSize = minSizeBefore,
            maxSize = maxSizeBefore
        ).drop(1).toList().reversed()
    }
    val eventsAfter = async {
        getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.FORWARDS,
            decryptionTimeout = decryptionTimeout,
            fetchTimeout = fetchTimeout,
            limitPerFetch = limitPerFetch,
            minSize = minSizeAfter,
            maxSize = maxSizeAfter
        ).drop(1).toList()
    }
    eventsBefore.await() + startEvent + eventsAfter.await()
}