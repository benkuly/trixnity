package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.StateFlow

interface CoroutineCacheValuesIndex<K> {
    suspend fun onPut(key: K)
    suspend fun onRemove(key: K)
    suspend fun getSubscriptionCount(key: K): StateFlow<Int>
}