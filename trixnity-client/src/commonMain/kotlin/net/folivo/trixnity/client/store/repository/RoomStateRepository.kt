package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event


interface RoomStateRepository : MapDeleteByRoomIdRepository<RoomStateRepositoryKey, String, Event<*>> {
    override fun serializeKey(firstKey: RoomStateRepositoryKey, secondKey: String): String =
        firstKey.roomId.full + firstKey.type + secondKey
}

data class RoomStateRepositoryKey(
    val roomId: RoomId,
    val type: String
)