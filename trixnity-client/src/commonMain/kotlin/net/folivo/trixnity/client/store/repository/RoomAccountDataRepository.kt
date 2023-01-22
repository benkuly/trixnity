package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

interface RoomAccountDataRepository :
    TwoDimensionsRepository<RoomAccountDataRepositoryKey, String, Event.RoomAccountDataEvent<*>> {

    override fun serializeKey(key: RoomAccountDataRepositoryKey): String =
        this::class.simpleName + key.roomId.full + key.type

    override fun serializeKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String): String =
        serializeKey(firstKey) + secondKey
}

data class RoomAccountDataRepositoryKey(
    val roomId: RoomId,
    val type: String,
)