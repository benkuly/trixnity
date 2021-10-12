package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.MatrixId

interface RoomRepository : MinimalStoreRepository<MatrixId.RoomId, Room> {
    suspend fun getAll(): List<Room>
}