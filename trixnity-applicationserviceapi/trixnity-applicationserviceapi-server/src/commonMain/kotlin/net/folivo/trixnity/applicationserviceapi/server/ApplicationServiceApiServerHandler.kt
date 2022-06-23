package net.folivo.trixnity.applicationserviceapi.server

import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent

interface ApplicationServiceApiServerHandler {
    suspend fun addTransaction(txnId: String, events: List<ClientEvent<*>>)
    suspend fun hasUser(userId: UserId)
    suspend fun hasRoomAlias(roomAlias: RoomAliasId)
}