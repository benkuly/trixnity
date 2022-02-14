package net.folivo.trixnity.client.store.repository

interface MinimalStoreRepository<K, V> {
    suspend fun get(key: K): V?
    suspend fun save(key: K, value: V)
    suspend fun delete(key: K)
    suspend fun deleteAll()
}