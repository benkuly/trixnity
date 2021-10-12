package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event


interface RoomStateRepository : MinimalStoreRepository<RoomStateRepositoryKey, Map<String, Event<*>>> {
    suspend fun getByStateKey(key: RoomStateRepositoryKey, stateKey: String): Event<*>?
    suspend fun saveByStateKey(key: RoomStateRepositoryKey, stateKey: String, event: Event<*>)
}

data class RoomStateRepositoryKey(
    val roomId: MatrixId.RoomId,
    val type: String
)