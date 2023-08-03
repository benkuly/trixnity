package net.folivo.trixnity.client.store.repository

object NoOpRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> readTransaction(block: suspend () -> T): T = block()

    override suspend fun writeTransaction(block: suspend () -> Unit) = block()
}