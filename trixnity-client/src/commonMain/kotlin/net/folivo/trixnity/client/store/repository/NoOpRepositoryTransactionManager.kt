package net.folivo.trixnity.client.store.repository

object NoOpRepositoryTransactionManager : RepositoryTransactionManager {
    override val parallelTransactionsSupported: Boolean = true
    override suspend fun <T> readTransaction(block: suspend () -> T): T = block()

    override suspend fun writeTransaction(block: suspend () -> Unit) = block()
}