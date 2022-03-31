package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

suspend inline fun <reified C : RoomAccountDataEventContent> RoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
    scope: CoroutineScope
): StateFlow<C?> {
    return getAccountData(roomId, C::class, key, scope)
}

suspend inline fun <reified C : RoomAccountDataEventContent> RoomService.getAccountData(
    roomId: RoomId,
    key: String = "",
): C? {
    return getAccountData(roomId, C::class, key)
}

suspend inline fun <reified C : StateEventContent> RoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
    scope: CoroutineScope
): StateFlow<Event<C>?> {
    return getState(roomId, stateKey, C::class, scope)
}

suspend inline fun <reified C : StateEventContent> RoomService.getState(
    roomId: RoomId,
    stateKey: String = "",
): Event<C>? {
    return getState(roomId, stateKey, C::class)
}

@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toList")
suspend fun Flow<StateFlow<TimelineEvent?>>.toFlowList(maxSize: MutableStateFlow<Int>): Flow<List<StateFlow<TimelineEvent?>>> =
    maxSize.flatMapLatest { listSize ->
        val list = mutableListOf<StateFlow<TimelineEvent?>>()
        take(listSize)
            .transform {
                list.add(it)
                emit(list as List<StateFlow<TimelineEvent?>>)
            }
    }

@OptIn(ExperimentalCoroutinesApi::class)
@JvmName("toListFromLatest")
suspend fun Flow<Flow<StateFlow<TimelineEvent?>>?>.toFlowList(maxSize: MutableStateFlow<Int>): Flow<List<StateFlow<TimelineEvent?>>> =
    flatMapLatest {
        it?.toFlowList(maxSize) ?: flowOf(listOf())
    }