package net.folivo.trixnity.client.store.repository

interface FullRepository<K, V> : MinimalRepository<K, V> {
    suspend fun getAll(): List<V>
}