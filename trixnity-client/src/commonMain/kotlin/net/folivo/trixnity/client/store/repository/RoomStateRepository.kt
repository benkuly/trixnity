package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent


interface RoomStateRepository : DeleteByRoomIdMapRepository<RoomStateRepositoryKey, String, StateBaseEvent<*>> {
    override fun serializeKey(firstKey: RoomStateRepositoryKey, secondKey: String): String =
        firstKey.roomId.full + firstKey.type + secondKey

    suspend fun getByRooms(roomIds: Set<RoomId>, type: String, stateKey: String): List<StateBaseEvent<*>>
}

data class RoomStateRepositoryKey(
    val roomId: RoomId,
    val type: String
)