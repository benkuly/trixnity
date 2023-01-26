package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager

class MediaCacheMappingStore(
    private val mediaCacheMappingRepository: MediaCacheMappingRepository,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope
) : Store {
    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        tm.writeOperation {
            mediaCacheMappingRepository.deleteAll()
        }
        uploadMediaCache.reset()
    }

    private val uploadMediaCache = MinimalRepositoryStateFlowCache(
        storeScope,
        mediaCacheMappingRepository,
        tm,
        config.cacheExpireDurations.mediaCacheMapping
    )

    suspend fun getMediaCacheMapping(cacheUri: String): MediaCacheMapping? =
        uploadMediaCache.get(cacheUri).first()

    suspend fun updateMediaCacheMapping(
        cacheUri: String,
        updater: suspend (oldMediaCacheMapping: MediaCacheMapping?) -> MediaCacheMapping?
    ) = uploadMediaCache.update(cacheUri, updater = updater)
}