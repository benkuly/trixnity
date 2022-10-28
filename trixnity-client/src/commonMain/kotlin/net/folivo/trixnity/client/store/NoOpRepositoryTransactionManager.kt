package net.folivo.trixnity.client.store

import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class NoOpRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> readTransaction(block: suspend () -> T): T = block()

    override suspend fun <T> writeTransaction(block: suspend () -> T): T = block()
}