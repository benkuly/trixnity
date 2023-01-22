package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.UserId

interface OutdatedKeysRepository : MinimalRepository<Long, Set<UserId>> {
    override fun serializeKey(key: Long): String = this::class.simpleName + key.toString()
}