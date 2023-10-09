package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent

interface RoomAccountDataRepository :
    MapDeleteByRoomIdRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>> {

    override fun serializeKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String): String =
        firstKey.roomId.full + firstKey.type + secondKey
}

data class RoomAccountDataRepositoryKey(
    val roomId: RoomId,
    val type: String,
)