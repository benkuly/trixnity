package net.folivo.trixnity.client.store.repository

interface MediaRepository : MinimalStoreRepository<String, ByteArray> {
    suspend fun changeUri(oldUri: String, newUri: String)
}