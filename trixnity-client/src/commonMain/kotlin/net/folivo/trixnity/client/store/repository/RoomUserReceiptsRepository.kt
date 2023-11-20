package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

interface RoomUserReceiptsRepository : MapDeleteByRoomIdRepository<RoomId, UserId, RoomUserReceipts> {
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String =
        firstKey.full + secondKey.full

    override suspend fun deleteByRoomId(roomId: RoomId)
}