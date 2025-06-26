package net.folivo.trixnity.client.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.jvm.JvmName

@Deprecated("use getTimeline without roomId instead")
@Suppress("DEPRECATION")
fun RoomService.getTimeline(
    roomId: RoomId,
    onStateChange: suspend (TimelineStateChange<Flow<TimelineEvent>>) -> Unit = {},
): SimpleTimeline = getTimeline(roomId = roomId, onStateChange = onStateChange) { it }

/**
 * @see RoomService.getTimeline
 */
fun RoomService.getTimeline(
    onStateChange: suspend (TimelineStateChange<Flow<TimelineEvent>>) -> Unit = {},
): SimpleTimeline = getTimeline(onStateChange = onStateChange) { it }

inline fun <reified C : RoomAccountDataEventContent> RoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
): Flow<C?> = getAccountData(roomId, C::class, key)

inline fun <reified C : StateEventContent> RoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
): Flow<StateBaseEvent<C>?> = getState(roomId, C::class, stateKey)

inline fun <reified C : StateEventContent> RoomService.getAllState(
    roomId: RoomId,
): Flow<Map<String, Flow<StateBaseEvent<C>?>>?> = getAllState(roomId, C::class)

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
 * Converts a flow of flows of timeline event into a flow of list of timeline events limited by [maxSize].
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
 * @param configStart The config for getting the [startFrom].
 * @param configBefore The config for getting [TimelineEvent]s before [startFrom].
 * @param configAfter The config for getting [TimelineEvent]s after [startFrom].
 *
 */
fun RoomService.getTimelineEventsAround(
    roomId: RoomId,
    startFrom: EventId,
    maxSizeBefore: StateFlow<Int>,
    maxSizeAfter: StateFlow<Int>,
    configStart: GetTimelineEventConfig.() -> Unit = {},
    configBefore: GetTimelineEventsConfig.() -> Unit = {},
    configAfter: GetTimelineEventsConfig.() -> Unit = {},
): Flow<List<Flow<TimelineEvent>>> =
    channelFlow {
        val startEvent = getTimelineEvent(roomId, startFrom, configStart).filterNotNull()
        startEvent.first()
        combine(
            getTimelineEvents(roomId, startFrom, GetEvents.Direction.BACKWARDS, configBefore)
                .drop(1)
                .toFlowList(maxSizeBefore)
                .map { it.reversed() },
            getTimelineEvents(roomId, startFrom, GetEvents.Direction.FORWARDS, configAfter)
                .drop(1)
                .toFlowList(maxSizeAfter),
        ) { beforeElements, afterElements ->
            beforeElements + startEvent + afterElements
        }.collectLatest { send(it) }
    }.buffer(0)

/**
 * Returns all timeline events around a starting event.
 *
 * @param configStart The config for getting the [startFrom].
 * @param configBefore The config for getting [TimelineEvent]s before [startFrom].
 * @param configAfter The config for getting [TimelineEvent]s after [startFrom].
 *
 * @see [RoomService.getTimelineEvents]
 *
 */
suspend fun RoomService.getTimelineEventsAround(
    roomId: RoomId,
    startFrom: EventId,
    configStart: GetTimelineEventConfig.() -> Unit = {},
    configBefore: GetTimelineEventsConfig.() -> Unit = {},
    configAfter: GetTimelineEventsConfig.() -> Unit = {},
): List<Flow<TimelineEvent>> = coroutineScope {
    val startEvent = getTimelineEvent(roomId, startFrom, configStart).filterNotNull()
    val eventsBefore = async {
        getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.BACKWARDS,
            config = configBefore,
        ).drop(1).toList().reversed()
    }
    val eventsAfter = async {
        getTimelineEvents(
            startFrom = startFrom,
            roomId = roomId,
            direction = GetEvents.Direction.FORWARDS,
            config = configAfter
        ).drop(1).toList()
    }
    eventsBefore.await() + startEvent + eventsAfter.await()
}

suspend fun Flow<TimelineEvent?>.firstWithContent(): TimelineEvent = filterNotNull().first { it.content != null }