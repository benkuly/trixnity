package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.RoomId

interface RoomRepository : FullRepository<RoomId, Room> {
    override fun serializeKey(key: RoomId): String = this::class.simpleName + key.full
}