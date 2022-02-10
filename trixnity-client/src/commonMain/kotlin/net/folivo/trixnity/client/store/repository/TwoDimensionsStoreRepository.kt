package net.folivo.trixnity.client.store.repository

interface TwoDimensionsStoreRepository<K1, K2, V> : MinimalStoreRepository<K1, Map<K2, V>> {
    suspend fun getBySecondKey(firstKey: K1, secondKey: K2): V?
    suspend fun saveBySecondKey(firstKey: K1, secondKey: K2, value: V)
    suspend fun deleteBySecondKey(firstKey: K1, secondKey: K2)
}