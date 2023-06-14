package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.StateFlow

interface CoroutineCacheStore<K, V> {
    suspend fun get(key: K): V?

    suspend fun persist(key: K, value: V?): StateFlow<Boolean>?

    suspend fun deleteAll()
}