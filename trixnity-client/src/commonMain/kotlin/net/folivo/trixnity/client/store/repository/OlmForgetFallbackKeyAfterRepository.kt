package net.folivo.trixnity.client.store.repository

import kotlinx.datetime.Instant

interface OlmForgetFallbackKeyAfterRepository : MinimalRepository<Long, Instant> {
    override fun serializeKey(key: Long): String = key.toString()
}