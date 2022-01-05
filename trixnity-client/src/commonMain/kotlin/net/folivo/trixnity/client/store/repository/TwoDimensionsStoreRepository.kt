package net.folivo.trixnity.client.store.repository

interface TwoDimensionsStoreRepository<K, V> : MinimalStoreRepository<K, Map<String, V>> {
    suspend fun getBySecondKey(firstKey: K, secondKey: String): V?
    suspend fun saveBySecondKey(firstKey: K, secondKey: String, value: V)
}