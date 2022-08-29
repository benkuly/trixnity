package net.folivo.trixnity.client.store

import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

class NoOpRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}