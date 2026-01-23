package de.connect2x.trixnity.client.media

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.cache.CacheValue
import de.connect2x.trixnity.client.store.cache.ConcurrentObservableMap
import de.connect2x.trixnity.client.store.cache.RemoverJobExecutingIndex
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.KeyedMutex
import de.connect2x.trixnity.utils.toByteArray
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private val log = Logger("de.connect2x.trixnity.client.CachedMediaStore")

abstract class CachedMediaStore(
    coroutineScope: CoroutineScope,
    config: MatrixClientConfiguration,
    clock: Clock,
) : MediaStore {
    private val mediaCacheMutex = KeyedMutex<String>()
    private val mediaCache = ConcurrentObservableMap<String, MutableStateFlow<CacheValue<ByteArray?>>>()
    private val cacheDisabled = config.cacheExpireDurations.media == ZERO
    private val mediaCacheRemoverJobExecutingIndex =
        RemoverJobExecutingIndex("media", mediaCache, clock, config.cacheExpireDurations.media)
            .also { mediaCache.indexes.update { indexes -> indexes + it } }

    init {
        if (cacheDisabled.not())
            coroutineScope.launch {
                while (isActive) {
                    delay(2.seconds)
                    mediaCacheRemoverJobExecutingIndex.invalidateCache()
                }
            }
    }

    abstract suspend fun deleteAllFromStore()

    final override suspend fun clearCache() {
        deleteAllFromStore()
        mediaCache.removeAll()
    }

    final override suspend fun deleteAll() {
        clearCache()
    }

    protected suspend fun toByteArray(
        uri: String,
        media: ByteArrayFlow,
        coroutineScope: CoroutineScope?,
        expectedSize: Long?,
        maxSize: Long?,
    ): ByteArray? =
        if (expectedSize == null || maxSize == null || expectedSize <= maxSize) {
            if (cacheDisabled) {
                if (maxSize != null) media.toByteArray(maxSize) else media.toByteArray()
            } else {
                val cacheValue = mediaCache.getOrPut(uri) { MutableStateFlow(CacheValue.Init()) }
                coroutineScope?.launch { cacheValue.collect() }
                cacheValue.value.valueOrNull()
                    .also { if (it != null) log.trace { "cache hit for $uri" } }
                    ?: mediaCacheMutex.withLock(uri) {
                        cacheValue.value.valueOrNull()
                            .also { if (it != null) log.trace { "deep cache hit for $uri" } }
                            ?: run {
                                log.trace { "load from store for $uri" }
                                val byteArray = if (maxSize != null) media.toByteArray(maxSize) else media.toByteArray()
                                cacheValue.value = CacheValue.Value(byteArray)
                                byteArray
                            }
                    }
            }
        } else {
            null
        }
}