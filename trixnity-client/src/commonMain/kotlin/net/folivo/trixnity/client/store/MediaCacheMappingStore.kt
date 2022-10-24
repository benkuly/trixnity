package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class MediaCacheMappingStore(
    private val mediaCacheMappingRepository: MediaCacheMappingRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope
) : Store {
    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        rtm.transaction {
            mediaCacheMappingRepository.deleteAll()
        }
        uploadMediaCache.reset()
    }

    private val uploadMediaCache = RepositoryStateFlowCache(storeScope, mediaCacheMappingRepository, rtm)

    suspend fun getMediaCacheMapping(cacheUri: String): MediaCacheMapping? =
        uploadMediaCache.get(cacheUri).first()

    suspend fun updateMediaCacheMapping(
        cacheUri: String,
        updater: suspend (oldMediaCacheMapping: MediaCacheMapping?) -> MediaCacheMapping?
    ) = uploadMediaCache.update(cacheUri, updater = updater)
}