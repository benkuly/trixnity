package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.UserId

interface OutdatedKeysRepository : MinimalRepository<Long, Set<UserId>> {
    override fun serializeKey(key: Long): String = key.toString()
}