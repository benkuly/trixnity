package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.core.model.UserId


interface UserPresenceRepository : MinimalRepository<UserId, UserPresence> {
    override fun serializeKey(key: UserId): String = key.full
}