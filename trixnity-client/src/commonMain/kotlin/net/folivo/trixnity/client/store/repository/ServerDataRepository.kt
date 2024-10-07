package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.ServerData

interface ServerDataRepository : MinimalRepository<Long, ServerData> {
    override fun serializeKey(key: Long): String = key.toString()
}