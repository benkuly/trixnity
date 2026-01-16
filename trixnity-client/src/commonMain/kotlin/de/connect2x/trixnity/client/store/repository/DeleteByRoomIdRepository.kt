package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId

interface DeleteByRoomIdRepository {
    suspend fun deleteByRoomId(roomId: RoomId)
}

interface DeleteByRoomIdFullRepository<K, V> : FullRepository<K, V>, DeleteByRoomIdRepository

interface DeleteByRoomIdMinimalRepository<K, V> : MinimalRepository<K, V>, DeleteByRoomIdRepository

interface DeleteByRoomIdMapRepository<K1, K2, V> : MapRepository<K1, K2, V>, DeleteByRoomIdRepository