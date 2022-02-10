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

    private suspend fun internalGet(
        key: K,
        withTransaction: Boolean = true,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
        scope: CoroutineScope?
    ): StateFlow<V?> = readWithCache(
        key,
        isContainedInCache = { infiniteCache || isContainedInCache(it) },
        retrieveAndUpdateCache = {
            if (infiniteCache) it
            else if (withTransaction) rtm.transaction { repository.get(key) }
            else repository.get(key)
        },
        scope
    )

    suspend fun get(
        key: K,
        withTransaction: Boolean = true,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
    ): V? = internalGet(key, withTransaction, isContainedInCache, scope = null).value

    suspend fun get(
        key: K,
        withTransaction: Boolean = true,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
        scope: CoroutineScope
    ): StateFlow<V?> = internalGet(key, withTransaction, isContainedInCache, scope)

    suspend fun update(
        key: K,
        persistIntoRepository: Boolean = true,
        withTransaction: Boolean = true,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
        onPersist: suspend (newValue: V?) -> Unit = {},
        updater: suspend (oldValue: V?) -> V?
    ) {
        writeWithCache(key, updater,
            isContainedInCache = { infiniteCache || isContainedInCache(it) },
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
                    onPersist(newValue)
                }
            })
    }
}