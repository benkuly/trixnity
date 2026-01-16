package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.ServerData

interface ServerDataRepository : MinimalRepository<Long, ServerData> {
    override fun serializeKey(key: Long): String = key.toString()
}