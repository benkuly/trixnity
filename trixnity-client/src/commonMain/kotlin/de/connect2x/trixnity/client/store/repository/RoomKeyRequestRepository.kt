package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredRoomKeyRequest

interface RoomKeyRequestRepository : FullRepository<String, StoredRoomKeyRequest> {
    override fun serializeKey(key: String): String = key
}