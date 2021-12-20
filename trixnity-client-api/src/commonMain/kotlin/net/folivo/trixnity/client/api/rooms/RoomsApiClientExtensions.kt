package net.folivo.trixnity.client.api.rooms

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

suspend inline fun <reified C : StateEventContent> RoomsApiClient.getStateEvent(
    roomId: RoomId,
    stateKey: String = "",
    asUserId: UserId? = null
): Result<C> = getStateEvent(C::class, roomId, stateKey, asUserId)

suspend inline fun <reified C : RoomAccountDataEventContent> RoomsApiClient.getAccountData(
    roomId: RoomId,
    userId: UserId,
    asUserId: UserId? = null
): Result<C> = getAccountData(C::class, roomId, userId, asUserId)