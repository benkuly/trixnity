package net.folivo.trixnity.client.store.repository

object NoOpRepositoryTransactionManager : RepositoryTransactionManager {
    override val supportsParallelWrite: Boolean = true
    override suspend fun <T> readTransaction(block: suspend () -> T): T = block()

    override suspend fun <T> writeTransaction(block: suspend () -> T): T = block()
}