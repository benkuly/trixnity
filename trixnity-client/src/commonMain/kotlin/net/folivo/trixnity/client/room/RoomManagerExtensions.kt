package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

suspend inline fun <reified C : RoomAccountDataEventContent> RoomManager.getAccountData(
    roomId: MatrixId.RoomId,
    scope: CoroutineScope
): StateFlow<C?> {
    return getAccountData(roomId, C::class, scope)
}

suspend inline fun <reified C : StateEventContent> RoomManager.getState(
    roomId: MatrixId.RoomId,
    stateKey: String = "",
    scope: CoroutineScope
): StateFlow<Event<C>?> {
    return getState(roomId, stateKey, C::class, scope)
}