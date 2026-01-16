package de.connect2x.trixnity.client.store.repository

interface FullRepository<K, V> : MinimalRepository<K, V> {
    suspend fun getAll(): List<V>
}