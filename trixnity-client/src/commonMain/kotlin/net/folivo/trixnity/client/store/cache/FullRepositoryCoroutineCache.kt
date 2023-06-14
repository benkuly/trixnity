package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.repository.FullRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class FullRepositoryCoroutineCache<K, V>(
    repository: FullRepository<K, V>,
    tm: TransactionManager,
    cacheScope: CoroutineScope,
    expireDuration: Duration = 1.minutes,
    private val valueToKeyMapper: (V) -> K,
) : CoroutineCache<K, V, FullRepositoryCoroutineCacheStore<K, V>>(
    name = repository::class.simpleName ?: repository::class.toString(),
    store = FullRepositoryCoroutineCacheStore(repository, tm),
    cacheScope = cacheScope,
    expireDuration = expireDuration
) {
    suspend fun fill() {
        store.getAll().forEach { value ->
            val key = valueToKeyMapper(value)
            updateAndGet(
                key = key,
                updater = null,
                get = { value },
                persist = { null }
            )
        }
    }
}