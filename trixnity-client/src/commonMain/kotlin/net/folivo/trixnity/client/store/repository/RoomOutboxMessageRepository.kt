package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomOutboxMessage

interface RoomOutboxMessageRepository : MinimalStoreRepository<String, RoomOutboxMessage> {
    suspend fun getAll(): List<RoomOutboxMessage>
}