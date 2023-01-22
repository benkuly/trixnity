package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.store.repository.MinimalRepository
import net.folivo.trixnity.client.store.repository.TwoDimensionsRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class TwoDimensionsRepositoryStateFlowCache<K1, K2, V, R : TwoDimensionsRepository<K1, K2, V>>(
    cacheScope: CoroutineScope,
    private val repository: R,
    private val tm: TransactionManager,
    expireDuration: Duration = 1.minutes,
) {

    private val cache = MinimalRepositoryStateFlowCache(
        cacheScope = cacheScope,
        repository = LoadingRepository(repository),
        tm = tm,
        expireDuration = expireDuration
    )

    fun reset() {
        cache.reset()
    }

    fun get(
        key: K1,
    ): Flow<Map<K2, V>?> =
        cache.get(key, isContainedInCache = { it?.fullyLoadedFromRepository == true })
            .map { it?.value }

    fun getBySecondKey(
        firstKey: K1,
        secondKey: K2,
    ): Flow<V?> =
        cache.readWithCache(
            key = firstKey,
            isContainedInCache = { isContainedInCacheBySecondKey(it, secondKey) },
            retrieveAndUpdateCache = { retrieveAndUpdateCacheBySecondKey(firstKey, secondKey, it) },
        ).map { it?.value?.get(secondKey) }

    suspend fun update(
        key: K1,
        persistIntoRepository: Boolean = true,
        onPersist: suspend (newValue: Map<K2, V>?) -> Unit = {},
        updater: suspend (oldValue: Map<K2, V>?) -> Map<K2, V>?
    ) = cache.update(
        key,
        persistIntoRepository,
        isContainedInCache = { it?.fullyLoadedFromRepository == true },
        onPersist = { onPersist(it?.value) },
        updater = { oldValue ->
            updater(oldValue?.value)
                ?.let { LoadingCacheValue(value = it, fullyLoadedFromRepository = true) }
        }
    )

    suspend fun saveBySecondKey(
        firstKey: K1,
        secondKey: K2,
        value: V?
    ) = cache.writeWithCache(
        key = firstKey,
        updater = { oldValue ->
            val newValue =
                if (value == null) oldValue?.value?.minus(secondKey)
                else oldValue?.value.orEmpty() + (secondKey to value)
            newValue?.let {
                LoadingCacheValue(value = it, fullyLoadedFromRepository = oldValue?.fullyLoadedFromRepository == true)
            }
        },
        isContainedInCache = { isContainedInCacheBySecondKey(it, secondKey) },
        retrieveAndUpdateCache = { it }, // there may be a value saved in db, but we don't need it
        persist = {
            val newValue = it?.value?.get(secondKey)
            tm.writeOperationAsync(repository.serializeKey(firstKey, secondKey)) {
                if (newValue == null) repository.deleteBySecondKey(firstKey, secondKey)
                else repository.saveBySecondKey(firstKey, secondKey, newValue)
            }
        })

    suspend fun updateBySecondKey(
        firstKey: K1,
        secondKey: K2,
        updater: suspend (V?) -> V?
    ) = cache.writeWithCache(
        key = firstKey,
        updater = { oldValue ->
            val value = updater(oldValue?.value?.get(secondKey))
            val newValue =
                if (value == null) oldValue?.value?.minus(secondKey)
                else oldValue?.value.orEmpty() + (secondKey to value)
            newValue?.let {
                LoadingCacheValue(value = it, fullyLoadedFromRepository = oldValue?.fullyLoadedFromRepository == true)
            }
        },
        isContainedInCache = { isContainedInCacheBySecondKey(it, secondKey) },
        retrieveAndUpdateCache = { retrieveAndUpdateCacheBySecondKey(firstKey, secondKey, it) },
        persist = {
            val value = it?.value?.get(secondKey)
            tm.writeOperationAsync(repository.serializeKey(firstKey, secondKey)) {
                if (value == null) repository.deleteBySecondKey(firstKey, secondKey)
                else repository.saveBySecondKey(firstKey, secondKey, value)
            }
        })


    private fun isContainedInCacheBySecondKey(
        it: LoadingCacheValue<Map<K2, V>>?,
        secondKey: K2
    ) = it?.fullyLoadedFromRepository == true || it?.value?.containsKey(secondKey) == true

    private suspend fun retrieveAndUpdateCacheBySecondKey(
        firstKey: K1,
        secondKey: K2,
        cacheValue: LoadingCacheValue<Map<K2, V>>?
    ): LoadingCacheValue<Map<K2, V>>? {
        val newValue = tm.readOperation {
            repository.getBySecondKey(firstKey, secondKey)
        }
        return if (newValue != null) LoadingCacheValue(
            value = cacheValue?.value.orEmpty() + (secondKey to newValue),
            fullyLoadedFromRepository = cacheValue?.fullyLoadedFromRepository == true
        )
        else cacheValue
    }
}

private data class LoadingCacheValue<T>(
    val value: T,
    val fullyLoadedFromRepository: Boolean
)

private class LoadingRepository<K1, K2, V>(
    private val baseRepository: TwoDimensionsRepository<K1, K2, V>
) : MinimalRepository<K1, LoadingCacheValue<Map<K2, V>>> {
    override fun serializeKey(key: K1): String = baseRepository.serializeKey(key)
    override suspend fun get(key: K1): LoadingCacheValue<Map<K2, V>>? =
        baseRepository.get(key)?.let { LoadingCacheValue(value = it, fullyLoadedFromRepository = true) }

    override suspend fun save(key: K1, value: LoadingCacheValue<Map<K2, V>>) =
        baseRepository.save(key, value.value)

    override suspend fun delete(key: K1) = baseRepository.delete(key)
    override suspend fun deleteAll() = baseRepository.deleteAll()
}