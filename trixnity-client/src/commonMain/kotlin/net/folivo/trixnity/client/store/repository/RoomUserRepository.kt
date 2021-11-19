package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

interface RoomUserRepository : MinimalStoreRepository<RoomId, Map<UserId, RoomUser>> {
    suspend fun getByUserId(userId: UserId, roomId: RoomId): RoomUser?
    suspend fun saveByUserId(userId: UserId, roomId: RoomId, roomUser: RoomUser)
    suspend fun deleteByUserId(userId: UserId, roomId: RoomId)
}