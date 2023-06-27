package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId

interface DeleteByRoomIdRepository {
    suspend fun deleteByRoomId(roomId: RoomId)
}

interface MinimalDeleteByRoomIdRepository<K, V> : MinimalRepository<K, V>, DeleteByRoomIdRepository

interface MapDeleteByRoomIdRepository<K1, K2, V> : MapRepository<K1, K2, V>, DeleteByRoomIdRepository