package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.core.model.UserId


interface UserPresenceRepository : MinimalRepository<UserId, UserPresence> {
    override fun serializeKey(key: UserId): String = key.full
}