package de.connect2x.trixnity.client.store.repository

interface MapRepository<K1, K2, V> {
    fun serializeKey(firstKey: K1, secondKey: K2): String
    suspend fun get(firstKey: K1): Map<K2, V>
    suspend fun get(firstKey: K1, secondKey: K2): V?
    suspend fun save(firstKey: K1, secondKey: K2, value: V)
    suspend fun delete(firstKey: K1, secondKey: K2)
    suspend fun deleteAll()
}