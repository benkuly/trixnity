package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class MinimalRepositoryStateFlowCache<K, V, R : MinimalRepository<K, V>>(
    cacheScope: CoroutineScope,
    private val repository: R,
    private val tm: TransactionManager,
    expireDuration: Duration = 1.minutes,
) : StateFlowCache<K, V>(
    repository::class.simpleName + repository.hashCode(),
    cacheScope,
    expireDuration
) {

    private val infiniteCache = expireDuration.isInfinite()

    private fun internalGet(
        key: K,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean,
    ): Flow<V?> = readWithCache(
        key,
        isContainedInCache = { infiniteCache || isContainedInCache(it) },
        retrieveAndUpdateCache = {
            if (infiniteCache) it
            else tm.readOperation { repository.get(key) }
        },
    )

    fun get(
        key: K,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
    ): Flow<V?> = internalGet(key, isContainedInCache)

    suspend fun save(
        key: K,
        value: V?,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
        onPersist: suspend (newValue: V?) -> Unit = {}
    ) {
        writeWithCache(key, { value },
            isContainedInCache = { infiniteCache || isContainedInCache(it) },
            retrieveAndUpdateCache = { it }, // there may be a value saved in db, but we don't need it
            persist = { newValue ->
                onPersist(newValue)
                tm.writeOperationAsync(repository.serializeKey(key)) {
                    if (newValue == null) repository.delete(key)
                    else repository.save(key, newValue)
                }
            })
    }

    suspend fun update(
        key: K,
        persistIntoRepository: Boolean = true,
        isContainedInCache: suspend (cacheValue: V?) -> Boolean = { it != null },
        onPersist: suspend (newValue: V?) -> Unit = {},
        updater: suspend (oldValue: V?) -> V?
    ) {
        writeWithCache(key, updater,
            isContainedInCache = { infiniteCache || isContainedInCache(it) },
            retrieveAndUpdateCache = { cacheValue ->
                if (infiniteCache) cacheValue
                else tm.readOperation { repository.get(key) }
            },
            persist = { newValue ->
                if (persistIntoRepository) {
                    onPersist(newValue)
                    tm.writeOperationAsync(repository.serializeKey(key)) {
                        if (newValue == null) repository.delete(key)
                        else repository.save(key, newValue)
                    }
                } else null
            })
    }
}