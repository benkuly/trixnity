package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class MediaCacheMappingStore(
    mediaCacheMappingRepository: MediaCacheMappingRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope
) : Store {
    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        uploadMediaCache.deleteAll()
    }

    private val uploadMediaCache = MinimalRepositoryObservableCache(
        mediaCacheMappingRepository,
        tm,
        storeScope,
        config.cacheExpireDurations.mediaCacheMapping
    ).also(statisticCollector::addCache)

    suspend fun getMediaCacheMapping(cacheUri: String): MediaCacheMapping? =
        uploadMediaCache.read(cacheUri).first()

    suspend fun updateMediaCacheMapping(
        cacheUri: String,
        updater: suspend (oldMediaCacheMapping: MediaCacheMapping?) -> MediaCacheMapping?
    ) = uploadMediaCache.write(cacheUri, updater = updater)

    suspend fun saveMediaCacheMapping(
        cacheUri: String,
        mediaCacheMapping: MediaCacheMapping
    ) = uploadMediaCache.write(cacheUri, mediaCacheMapping)

    suspend fun deleteMediaCacheMapping(cacheUri: String) = uploadMediaCache.write(cacheUri, null)
}