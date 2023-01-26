package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomOutboxMessage

interface RoomOutboxMessageRepository : FullRepository<String, RoomOutboxMessage<*>> {
    override fun serializeKey(key: String): String = this::class.simpleName + key
}