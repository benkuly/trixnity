package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event


interface RoomStateRepository : TwoDimensionsRepository<RoomStateRepositoryKey, String, Event<*>> {
    override fun serializeKey(key: RoomStateRepositoryKey): String =
        this::class.simpleName + key.roomId.full + key.type

    override fun serializeKey(firstKey: RoomStateRepositoryKey, secondKey: String): String =
        serializeKey(firstKey) + secondKey
}

data class RoomStateRepositoryKey(
    val roomId: RoomId,
    val type: String
)