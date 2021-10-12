package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId

interface RoomUserRepository : MinimalStoreRepository<RoomId, Map<UserId, RoomUser>> {
    suspend fun getByUserId(userId: UserId, roomId: RoomId): RoomUser?
    suspend fun saveByUserId(userId: UserId, roomId: RoomId, roomUser: RoomUser)
    suspend fun deleteByUserId(userId: UserId, roomId: RoomId)
}