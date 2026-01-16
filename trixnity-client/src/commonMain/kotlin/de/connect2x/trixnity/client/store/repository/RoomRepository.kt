package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.core.model.RoomId

interface RoomRepository : FullRepository<RoomId, Room> {
    override fun serializeKey(key: RoomId): String = key.full
}