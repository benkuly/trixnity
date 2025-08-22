package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryObservableCache
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.time.Clock

class MediaCacheMappingStore(
    mediaCacheMappingRepository: MediaCacheMappingRepository,
    tm: RepositoryTransactionManager,
    config: MatrixClientConfiguration,
    statisticCollector: ObservableCacheStatisticCollector,
    storeScope: CoroutineScope,
    clock: Clock,
) : Store {
    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        uploadMediaCache.deleteAll()
    }

    private val uploadMediaCache = MinimalRepositoryObservableCache(
        mediaCacheMappingRepository,
        tm,
        storeScope,
        clock,
        config.cacheExpireDurations.mediaCacheMapping
    ).also(statisticCollector::addCache)

    suspend fun getMediaCacheMapping(cacheUri: String): MediaCacheMapping? =
        uploadMediaCache.get(cacheUri).first()

    suspend fun updateMediaCacheMapping(
        cacheUri: String,
        updater: suspend (oldMediaCacheMapping: MediaCacheMapping?) -> MediaCacheMapping?
    ) = uploadMediaCache.update(cacheUri, updater = updater)

    suspend fun saveMediaCacheMapping(
        cacheUri: String,
        mediaCacheMapping: MediaCacheMapping
    ) = uploadMediaCache.set(cacheUri, mediaCacheMapping)

    suspend fun deleteMediaCacheMapping(cacheUri: String) = uploadMediaCache.set(cacheUri, null)
}