package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

interface TransactionManager {
    suspend fun transaction(block: suspend CoroutineScope.() -> Unit)
}

class TransactionManagerImpl(private val repositoryTransactionManager: RepositoryTransactionManager) :
    TransactionManager {
    override suspend fun transaction(block: suspend CoroutineScope.() -> Unit) =
        repositoryTransactionManager.writeTransaction {
            withContext(KeyStore.SkipOutdatedKeys) {
                block()
            }
        }
}