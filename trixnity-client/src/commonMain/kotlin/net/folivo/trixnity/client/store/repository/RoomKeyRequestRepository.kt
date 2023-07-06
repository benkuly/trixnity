package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredRoomKeyRequest

interface RoomKeyRequestRepository : FullRepository<String, StoredRoomKeyRequest> {
    override fun serializeKey(key: String): String = key
}