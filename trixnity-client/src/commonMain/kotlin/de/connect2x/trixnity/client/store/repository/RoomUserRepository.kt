package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

interface RoomUserRepository : DeleteByRoomIdMapRepository<RoomId, UserId, RoomUser> {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String =
        firstKey.full + secondKey.full

    override suspend fun deleteByRoomId(roomId: RoomId)
}