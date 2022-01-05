package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.TwoDimensionsStoreRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TwoDimensionsRepositoryStateFlowCache<K, V, R : TwoDimensionsStoreRepository<K, V>>(
    cacheScope: CoroutineScope,
    private val repository: R,
    private val rtm: RepositoryTransactionManager,
    cacheDuration: Duration = 1.minutes,
) : RepositoryStateFlowCache<K, Map<String, V>, R>(
    cacheScope = cacheScope,
    repository = repository,
    rtm = rtm,
    infiniteCache = false,
    cacheDuration = cacheDuration
) {

    suspend fun updateBySecondKey(
        firstKey: K,
        secondKey: String,
        value: V
    ) = writeWithCache(
        key = firstKey,
        updater = { it?.plus(secondKey to value) ?: mapOf(secondKey to value) },
        // We don't mind, what is stored in database, because we always override it.
        containsInCache = { true },
        retrieveAndUpdateCache = { null },
        persist = {
            rtm.transaction {
                repository.saveBySecondKey(firstKey, secondKey, value)
            }
        })

    private suspend fun getBySecondKeyAsFlow(
        firstKey: K,
        secondKey: String,
        scope: CoroutineScope? = null
    ) = readWithCache(
        key = firstKey,
        containsInCache = { it?.containsKey(secondKey) ?: false },
        retrieveAndUpdateCache = { cacheValue ->
            val newValue = rtm.transaction {
                repository.getBySecondKey(firstKey, secondKey)
            }
            if (newValue != null) cacheValue?.plus(secondKey to newValue) ?: mapOf(secondKey to newValue)
            else cacheValue
        },
        scope
    ).map { it?.get(secondKey) }

    suspend fun getBySecondKey(
        firstKey: K,
        secondKey: String,
        scope: CoroutineScope
    ): StateFlow<V?> {
        return getBySecondKeyAsFlow(firstKey, secondKey, scope).stateIn(scope)
    }

    suspend fun getBySecondKey(
        firstKey: K,
        secondKey: String,
    ): V? {
        return getBySecondKeyAsFlow(firstKey, secondKey).firstOrNull()
    }
}