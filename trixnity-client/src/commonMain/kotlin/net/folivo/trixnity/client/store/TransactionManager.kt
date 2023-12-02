package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

interface TransactionManager {
    suspend fun <T> readTransaction(block: suspend CoroutineScope.() -> T): T
    suspend fun writeTransaction(block: suspend CoroutineScope.() -> Unit)
}

class TransactionManagerImpl(private val repositoryTransactionManager: RepositoryTransactionManager) :
    TransactionManager {
    override suspend fun <T> readTransaction(block: suspend CoroutineScope.() -> T): T =
        repositoryTransactionManager.readTransaction {
            coroutineScope {
                block()
            }
        }

    override suspend fun writeTransaction(block: suspend CoroutineScope.() -> Unit) =
        repositoryTransactionManager.writeTransaction {
            withContext(KeyStore.SkipOutdatedKeys) {
                block()
            }
        }
}