package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.Authentication

interface AuthenticationRepository : MinimalRepository<Long, Authentication> {
    override fun serializeKey(key: Long): String = key.toString()
}