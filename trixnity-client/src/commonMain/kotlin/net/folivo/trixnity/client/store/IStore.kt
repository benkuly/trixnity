package net.folivo.trixnity.client.store

interface IStore {
    suspend fun init()

    /**
     * Only deletes everything, that can be fetched from server.
     */
    suspend fun clearCache()

    /**
     * Deletes everything.
     */
    suspend fun deleteAll()
}