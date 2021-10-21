package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

suspend inline fun <reified C : RoomAccountDataEventContent> RoomManager.getAccountData(
    roomId: MatrixId.RoomId,
    scope: CoroutineScope
): StateFlow<C?> {
    return getAccountData(roomId, C::class, scope)
}