package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.cache.withCacheTransaction
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager


interface TransactionManager {
    suspend fun writeTransaction(block: suspend CoroutineScope.() -> Unit)
}

class TransactionManagerImpl(private val repositoryTransactionManager: RepositoryTransactionManager) :
    TransactionManager {
    override suspend fun writeTransaction(block: suspend CoroutineScope.() -> Unit) {
        withCacheTransaction { transaction ->
            repositoryTransactionManager.writeTransaction {
                withContext(KeyStore.SkipOutdatedKeys + transaction) {
                    block()
                }
            }
        }
    }
}