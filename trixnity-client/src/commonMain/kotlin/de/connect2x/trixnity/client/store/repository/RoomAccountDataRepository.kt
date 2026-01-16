package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent

interface RoomAccountDataRepository :
    DeleteByRoomIdMapRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>> {

    override fun serializeKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String): String =
        firstKey.roomId.full + firstKey.type + secondKey
}

data class RoomAccountDataRepositoryKey(
    val roomId: RoomId,
    val type: String,
)