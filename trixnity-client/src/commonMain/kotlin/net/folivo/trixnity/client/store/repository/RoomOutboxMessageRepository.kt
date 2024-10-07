package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.core.model.RoomId

interface RoomOutboxMessageRepository :
    DeleteByRoomIdFullRepository<RoomOutboxMessageRepositoryKey, RoomOutboxMessage<*>> {
    override fun serializeKey(key: RoomOutboxMessageRepositoryKey): String = key.roomId.full + key.transactionId
}

data class RoomOutboxMessageRepositoryKey(val roomId: RoomId, val transactionId: String)