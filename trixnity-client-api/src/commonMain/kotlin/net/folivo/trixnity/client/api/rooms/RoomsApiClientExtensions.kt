package net.folivo.trixnity.client.api.rooms

import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

suspend inline fun <reified C : StateEventContent> RoomsApiClient.getStateEvent(
    roomId: RoomId,
    stateKey: String = "",
    asUserId: UserId? = null
): C {
    return getStateEvent(C::class, roomId, stateKey, asUserId)
}

suspend inline fun <reified C : RoomAccountDataEventContent> RoomsApiClient.getAccountData(
    roomId: RoomId,
    userId: UserId,
    asUserId: UserId? = null
): C {
    return getAccountData(C::class, roomId, userId, asUserId)
}