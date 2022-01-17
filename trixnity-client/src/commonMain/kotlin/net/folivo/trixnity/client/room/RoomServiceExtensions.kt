package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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