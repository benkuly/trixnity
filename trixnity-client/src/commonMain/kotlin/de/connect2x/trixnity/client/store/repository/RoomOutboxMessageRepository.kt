package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.core.model.RoomId

interface RoomOutboxMessageRepository :
    DeleteByRoomIdFullRepository<RoomOutboxMessageRepositoryKey, RoomOutboxMessage<*>> {
    override fun serializeKey(key: RoomOutboxMessageRepositoryKey): String = key.roomId.full + key.transactionId
}

data class RoomOutboxMessageRepositoryKey(val roomId: RoomId, val transactionId: String)