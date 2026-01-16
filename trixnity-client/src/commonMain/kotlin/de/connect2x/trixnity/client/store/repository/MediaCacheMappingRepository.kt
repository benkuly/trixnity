package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.MediaCacheMapping

interface MediaCacheMappingRepository : MinimalRepository<String, MediaCacheMapping> {
    override fun serializeKey(key: String): String = key
}