package de.connect2x.trixnity.client.store.repository

interface MinimalRepository<K, V> {
    fun serializeKey(key: K): String
    suspend fun get(key: K): V?
    suspend fun save(key: K, value: V)
    suspend fun delete(key: K)
    suspend fun deleteAll()
}