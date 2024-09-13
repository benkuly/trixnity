package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.ServerVersions

interface ServerVersionsRepository : MinimalRepository<Long, ServerVersions> {
    override fun serializeKey(key: Long): String = key.toString()
}