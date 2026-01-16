package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.Authentication

interface AuthenticationRepository : MinimalRepository<Long, Authentication> {
    override fun serializeKey(key: Long): String = key.toString()
}