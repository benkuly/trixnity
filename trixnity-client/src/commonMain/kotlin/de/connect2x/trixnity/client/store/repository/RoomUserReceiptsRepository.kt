package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

interface RoomUserReceiptsRepository : DeleteByRoomIdMapRepository<RoomId, UserId, RoomUserReceipts> {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String =
        firstKey.full + secondKey.full

    override suspend fun deleteByRoomId(roomId: RoomId)
}