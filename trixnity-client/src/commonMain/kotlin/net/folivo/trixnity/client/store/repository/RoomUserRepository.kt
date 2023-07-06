package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

interface RoomUserRepository : MapDeleteByRoomIdRepository<RoomId, UserId, RoomUser> {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String =
        firstKey.full + secondKey.full

    override suspend fun deleteByRoomId(roomId: RoomId)
}