package net.folivo.trixnity.client.store.repository

/**
 * This must be implemented by new database implementations.
 */
interface RepositoryTransactionManager {
    val parallelTransactionsSupported: Boolean
    suspend fun <T> readTransaction(block: suspend () -> T): T
    suspend fun writeTransaction(block: suspend () -> Unit)
}