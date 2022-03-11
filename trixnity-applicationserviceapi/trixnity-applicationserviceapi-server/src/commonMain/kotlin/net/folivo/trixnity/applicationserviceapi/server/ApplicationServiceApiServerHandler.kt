package net.folivo.trixnity.applicationserviceapi.server

import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

interface ApplicationServiceApiServerHandler {
    suspend fun addTransaction(tnxId: String, events: List<Event<*>>)
    suspend fun hasUser(userId: UserId): Boolean
    suspend fun hasRoomAlias(roomAlias: RoomAliasId): Boolean
}