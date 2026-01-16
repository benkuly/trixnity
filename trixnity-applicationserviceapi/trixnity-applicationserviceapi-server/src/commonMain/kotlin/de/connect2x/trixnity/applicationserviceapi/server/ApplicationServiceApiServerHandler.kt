package de.connect2x.trixnity.applicationserviceapi.server

import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent

interface ApplicationServiceApiServerHandler {
    suspend fun addTransaction(txnId: String, events: List<RoomEvent<*>>)
    suspend fun hasUser(userId: UserId)
    suspend fun hasRoomAlias(roomAlias: RoomAliasId)
    suspend fun ping(txnId: String?)
}