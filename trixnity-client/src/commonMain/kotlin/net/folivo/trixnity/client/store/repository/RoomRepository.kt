package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.RoomId

interface RoomRepository : MinimalStoreRepository<RoomId, Room> {
    suspend fun getAll(): List<Room>
}