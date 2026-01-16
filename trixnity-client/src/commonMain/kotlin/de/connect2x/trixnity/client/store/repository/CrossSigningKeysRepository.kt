package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.core.model.UserId

interface CrossSigningKeysRepository : MinimalRepository<UserId, Set<StoredCrossSigningKeys>> {
    override fun serializeKey(key: UserId): String = key.full
}