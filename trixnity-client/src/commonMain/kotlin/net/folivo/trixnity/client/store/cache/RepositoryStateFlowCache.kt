package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class RepositoryStateFlowCache<K, V, R : MinimalStoreRepository<K, V>>(
    cacheScope: CoroutineScope,
    private val repository: R,
    private val rtm: RepositoryTransactionManager,
    infiniteCache: Boolean = false,
    cacheDuration: Duration = 1.minutes,
) : StateFlowCache<K, V>(cacheScope, infiniteCache, cacheDuration) {

    suspend fun get(
        key: K,
        withTransaction: Boolean = true
    ): V? = readWithCache(
        key,
        containsInCache = { it != null },
        retrieveAndUpdateCache = {
            if (withTransaction) rtm.transaction { repository.get(key) }
            else repository.get(key)
        },
        null
    ).value

    suspend fun get(
        key: K,
        scope: CoroutineScope
    ): StateFlow<V?> {
        return readWithCache(
            key,
            containsInCache = { it != null },
            retrieveAndUpdateCache = { rtm.transaction { repository.get(key) } },
            scope
        )
    }

    suspend fun getWithInfiniteMode(
        key: K,
    ): StateFlow<V?> {
        return readWithCache(
            key,
            containsInCache = { infiniteCache || it != null },
            retrieveAndUpdateCache = { cacheValue ->
                if (infiniteCache) cacheValue
                else rtm.transaction { repository.get(key) }
            },
            null
        )
    }

    suspend fun update(
        key: K,
        withTransaction: Boolean = true,
        updater: suspend (oldValue: V?) -> V?
    ) = update(
        key = key,
        persistIntoRepository = true,
        withTransaction = withTransaction,
        updater = updater
    )

    suspend fun update(
        key: K,
        persistIntoRepository: Boolean = true,
        withTransaction: Boolean = true,
        updater: suspend (oldValue: V?) -> V?
    ) {
        writeWithCache(key, updater,
            containsInCache = { infiniteCache || it != null },
            retrieveAndUpdateCache = { cacheValue ->
                if (infiniteCache) cacheValue
                else if (withTransaction) rtm.transaction { repository.get(key) } else repository.get(key)
            },
            persist = { newValue ->
                if (persistIntoRepository) {
                    if (withTransaction) rtm.transaction {
                        if (newValue == null) repository.delete(key)
                        else repository.save(key, newValue)
                    } else {
                        if (newValue == null) repository.delete(key)
                        else repository.save(key, newValue)
                    }
                }
            })
    }
}