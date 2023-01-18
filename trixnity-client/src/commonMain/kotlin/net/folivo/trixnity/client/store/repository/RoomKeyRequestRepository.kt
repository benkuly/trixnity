package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredRoomKeyRequest

interface RoomKeyRequestRepository : MinimalStoreRepository<String, StoredRoomKeyRequest> {
    suspend fun getAll(): List<StoredRoomKeyRequest>
}