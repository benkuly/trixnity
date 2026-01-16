package de.connect2x.trixnity.client.store.repository

import kotlin.time.Instant

interface OlmForgetFallbackKeyAfterRepository : MinimalRepository<Long, Instant> {
    override fun serializeKey(key: Long): String = key.toString()
}