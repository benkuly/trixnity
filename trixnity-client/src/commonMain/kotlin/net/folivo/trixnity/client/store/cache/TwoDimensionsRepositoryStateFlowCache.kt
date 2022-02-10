package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import net.folivo.trixnity.client.store.repository.TwoDimensionsStoreRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private data class LoadingCacheValue<T>(
    val value: T,
    val fullyLoadedFromRepository: Boolean
)

private class LoadingRepository<K1, K2, V>(
    private val baseRepository: TwoDimensionsStoreRepository<K1, K2, V>
) : MinimalStoreRepository<K1, LoadingCacheValue<Map<K2, V>>> {
    override suspend fun get(key: K1): LoadingCacheValue<Map<K2, V>>? =
        baseRepository.get(key)?.let { LoadingCacheValue(it, true) }

    override suspend fun save(key: K1, value: LoadingCacheValue<Map<K2, V>>) =
        baseRepository.save(key, value.value)

    override suspend fun delete(key: K1) = baseRepository.delete(key)
}

class TwoDimensionsRepositoryStateFlowCache<K1, K2, V, R : TwoDimensionsStoreRepository<K1, K2, V>>(
    cacheScope: CoroutineScope,
    private val repository: R,
    private val rtm: RepositoryTransactionManager,
    cacheDuration: Duration = 1.minutes,
) {

    private val cache = RepositoryStateFlowCache(
        cacheScope = cacheScope,
        repository = LoadingRepository(repository),
        rtm = rtm,
        infiniteCache = false,
        cacheDuration = cacheDuration
    )

    suspend fun get(
        key: K1,
        withTransaction: Boolean = true,
    ): Map<K2, V>? {
        return cache.get(key, withTransaction, isContainedInCache = { it?.fullyLoadedFromRepository == true })?.value
    }

    suspend fun get(
        key: K1,
        withTransaction: Boolean = true,
        scope: CoroutineScope
    ): StateFlow<Map<K2, V>?> =
        cache.get(key, withTransaction, isContainedInCache = { it?.fullyLoadedFromRepository == true }, scope)
            .map { it?.value }
            .stateIn(scope)

    suspend fun update(
        key: K1,
        persistIntoRepository: Boolean = true,
        withTransaction: Boolean = true,
        onPersist: suspend (newValue: Map<K2, V>?) -> Unit = {},
        updater: suspend (oldValue: Map<K2, V>?) -> Map<K2, V>?
    ) = cache.update(
        key,
        persistIntoRepository,
        withTransaction,
        isContainedInCache = { it?.fullyLoadedFromRepository == true },
        onPersist = { onPersist(it?.value) },
        updater = { oldValue -> updater(oldValue?.value)?.let { LoadingCacheValue(it, true) } }
    )

    suspend fun updateBySecondKey(
        firstKey: K1,
        secondKey: K2,
        updater: suspend (V?) -> V?
    ) = cache.writeWithCache(
        key = firstKey,
        updater = { oldValue ->
            val value = updater(oldValue?.value?.get(secondKey))
            val newValue = if (value == null) oldValue?.value?.minus(secondKey)
            else oldValue?.value?.plus(secondKey to value) ?: mapOf(secondKey to value)
            newValue?.let { LoadingCacheValue(it, oldValue?.fullyLoadedFromRepository == true) }
        },
        // We don't mind, what is stored in database, because we always override it.
        isContainedInCache = { true },
        retrieveAndUpdateCache = { null },
        persist = {
            val value = it?.value?.get(secondKey)
            rtm.transaction {
                if (value == null) repository.deleteBySecondKey(firstKey, secondKey)
                else repository.saveBySecondKey(firstKey, secondKey, value)
            }
        })

    private suspend fun getBySecondKeyAsFlow(
        firstKey: K1,
        secondKey: K2,
        scope: CoroutineScope? = null
    ) = cache.readWithCache(
        key = firstKey,
        isContainedInCache = { it?.value?.containsKey(secondKey) ?: false },
        retrieveAndUpdateCache = { cacheValue ->
            val newValue = rtm.transaction {
                repository.getBySecondKey(firstKey, secondKey)
            }
            if (newValue != null) LoadingCacheValue(
                cacheValue?.value?.plus(secondKey to newValue) ?: mapOf(
                    secondKey to newValue
                ), cacheValue?.fullyLoadedFromRepository == true
            )
            else cacheValue
        },
        scope
    ).map { it?.value?.get(secondKey) }

    suspend fun getBySecondKey(
        firstKey: K1,
        secondKey: K2,
        scope: CoroutineScope
    ): StateFlow<V?> {
        return getBySecondKeyAsFlow(firstKey, secondKey, scope).stateIn(scope)
    }

    suspend fun getBySecondKey(
        firstKey: K1,
        secondKey: K2,
    ): V? {
        return getBySecondKeyAsFlow(firstKey, secondKey).firstOrNull()
    }
}