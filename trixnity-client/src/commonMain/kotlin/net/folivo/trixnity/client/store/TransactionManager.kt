package net.folivo.trixnity.client.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.utils.concurrentMutableList
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger("net.folivo.trixnity.client.store.TransactionManager")

interface TransactionManager {
    suspend fun transaction(block: suspend CoroutineScope.() -> Unit)
}

class Transaction : CoroutineContext.Element {
    val onCommitActions = concurrentMutableList<suspend () -> Unit>()
    val onRollbackActions = concurrentMutableList<suspend () -> Unit>()

    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<Transaction>
}

class TransactionManagerImpl(private val repositoryTransactionManager: RepositoryTransactionManager) :
    TransactionManager {
    override suspend fun transaction(block: suspend CoroutineScope.() -> Unit) {
        val transaction = Transaction()
        try {
            repositoryTransactionManager.writeTransaction {
                withContext(KeyStore.SkipOutdatedKeys + transaction) {
                    block()
                }
            }
            val onCommitActions = transaction.onCommitActions.read { toList() }
            if (onCommitActions.isNotEmpty()) {
                log.trace { "apply commit actions for transaction" }
                onCommitActions.forEach { it() }
            }
        } catch (exception: Exception) {
            withContext(NonCancellable) {
                val onRollbackActions = transaction.onRollbackActions.read { reversed() }
                if (onRollbackActions.isNotEmpty()) {
                    log.debug { "apply rollback actions for transaction" }
                    onRollbackActions.forEach { it() }
                }
            }
            throw exception
        }
    }
}