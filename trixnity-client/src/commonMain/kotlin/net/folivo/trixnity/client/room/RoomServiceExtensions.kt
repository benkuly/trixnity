package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.jvm.JvmName

suspend inline fun <reified C : RoomAccountDataEventContent> IRoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
    scope: CoroutineScope
): StateFlow<C?> {
    return getAccountData(roomId, C::class, key, scope)
}

suspend inline fun <reified C : RoomAccountDataEventContent> IRoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
): C? {
    return getAccountData(roomId, C::class, key)
}

suspend inline fun <reified C : StateEventContent> IRoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
    scope: CoroutineScope
): StateFlow<Event<C>?> {
    return getState(roomId, stateKey, C::class, scope)
}

suspend inline fun <reified C : StateEventContent> IRoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
): Event<C>? {
    return getState(roomId, stateKey, C::class)
}

/**
 * Converts a flow of timeline events into a flow of list of timeline events limited by `maxSize`.
 *
 * ```
 * Input: (E) -> (E) -> (E) -> delay e. g. due to fetching new events -> (E)
 * Output: ([EEE]) -> delay -> ([EEEE])
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toList")
fun Flow<StateFlow<TimelineEvent?>>.toFlowList(
    maxSize: StateFlow<Int>,
    minSize: MutableStateFlow<Int> = MutableStateFlow(0)
): Flow<List<StateFlow<TimelineEvent?>>> {
    return maxSize.flatMapLatest { listSize ->
        take(listSize)
            .scan<StateFlow<TimelineEvent?>, List<StateFlow<TimelineEvent?>>>(listOf()) { old, new -> old + new }
            .filter { it.size > minSize.value }
            .onEach { minSize.value = it.size }
    }
}

/**
 * Converts a flow of flow of timeline events into a flow of list of timeline events limited by `maxSize`.
 *
 * ```
 * Input: (E) -> (E) -> (E) -> delay e. g. due to fetching new events -> (E)
 * Output: ([EEE]) -> delay -> ([EEEE])
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toListFromLatest")
fun Flow<Flow<StateFlow<TimelineEvent?>>?>.toFlowList(
    maxSize: StateFlow<Int>,
    minSize: MutableStateFlow<Int> = MutableStateFlow(0)
): Flow<List<StateFlow<TimelineEvent?>>> =
    flatMapLatest {
        it?.toFlowList(maxSize, minSize) ?: flowOf(listOf())
    }