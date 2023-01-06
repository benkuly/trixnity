package net.folivo.trixnity.client.store.repository

interface RepositoryTransactionManager {
    val supportsParallelWrite: Boolean
    suspend fun <T> readTransaction(block: suspend () -> T): T
    suspend fun <T> writeTransaction(block: suspend () -> T): T
}