package net.folivo.trixnity.client.user

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent

inline fun <reified C : RoomEventContent> UserService.canSendEvent(roomId: RoomId) =
    canSendEvent(roomId, C::class)

inline fun <reified C : GlobalAccountDataEventContent> UserService.getAccountData(
    key: String = "",
): Flow<C?> {
    return getAccountData(C::class, key)
}