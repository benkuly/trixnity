package net.folivo.trixnity.client.store.transaction

/**
 * This must be implemented by new database implementations.
 */
interface RepositoryTransactionManager {
    suspend fun <T> readTransaction(block: suspend () -> T): T
    suspend fun writeTransaction(block: suspend () -> Unit)
}