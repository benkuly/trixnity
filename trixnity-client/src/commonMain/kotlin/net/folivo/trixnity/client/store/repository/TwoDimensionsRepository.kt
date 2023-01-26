package net.folivo.trixnity.client.store.repository

interface TwoDimensionsRepository<K1, K2, V> : MinimalRepository<K1, Map<K2, V>> {
    fun serializeKey(firstKey: K1, secondKey: K2): String
    suspend fun getBySecondKey(firstKey: K1, secondKey: K2): V?
    suspend fun saveBySecondKey(firstKey: K1, secondKey: K2, value: V)
    suspend fun deleteBySecondKey(firstKey: K1, secondKey: K2)
}