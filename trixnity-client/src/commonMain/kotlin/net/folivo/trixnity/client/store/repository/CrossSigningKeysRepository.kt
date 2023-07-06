package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.core.model.UserId

interface CrossSigningKeysRepository : MinimalRepository<UserId, Set<StoredCrossSigningKeys>> {
    override fun serializeKey(key: UserId): String = key.full
}