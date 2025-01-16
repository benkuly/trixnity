package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.utils.concurrentMutableList
import kotlin.coroutines.CoroutineContext

interface TransactionManager {
    suspend fun transaction(block: suspend CoroutineScope.() -> Unit)
}

class Transaction : CoroutineContext.Element {
    val doAfter = concurrentMutableList<suspend () -> Unit>()

    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<Transaction>
}

class TransactionManagerImpl(private val repositoryTransactionManager: RepositoryTransactionManager) :
    TransactionManager {
    override suspend fun transaction(block: suspend CoroutineScope.() -> Unit) {
        val transaction = Transaction()
        repositoryTransactionManager.writeTransaction {
            withContext(KeyStore.SkipOutdatedKeys + transaction) {
                block()
            }
        }
        transaction.doAfter.read { toList() }.forEach { it() }
    }
}