package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.MediaCacheMapping

interface MediaCacheMappingRepository : MinimalRepository<String, MediaCacheMapping> {
    override fun serializeKey(key: String): String = key
}