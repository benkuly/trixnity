package net.folivo.trixnity.appservice.rest

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event

interface AppserviceService {
    suspend fun addTransactions(tnxId: String, events: Flow<Event<*>>)
    suspend fun hasUser(userId: MatrixId.UserId): Boolean
    suspend fun hasRoomAlias(roomAlias: MatrixId.RoomAliasId): Boolean
}