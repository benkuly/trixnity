package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.coroutines.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import kotlin.coroutines.CoroutineContext

class ExposedReadTransaction(
    val transaction: Transaction,
    val transactionCoroutineContext: CoroutineContext,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ExposedReadTransaction>
}

class ExposedWriteTransaction(
    val transaction: Transaction,
    val transactionCoroutineContext: CoroutineContext,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ExposedWriteTransaction>
}

suspend fun <T> withExposedRead(block: () -> T) = coroutineScope {
    val exposedReadTransaction = checkNotNull(coroutineContext[ExposedReadTransaction])
    withContext(exposedReadTransaction.transactionCoroutineContext) {
        exposedReadTransaction.transaction.suspendedTransaction { block() }
    }
}

suspend fun <T> withExposedWrite(block: () -> T) = coroutineScope {
    val exposedWriteTransaction = checkNotNull(coroutineContext[ExposedWriteTransaction])
    withContext(exposedWriteTransaction.transactionCoroutineContext) {
        exposedWriteTransaction.transaction.suspendedTransaction { block() }
    }
}

class ExposedRepositoryTransactionManager(
    private val database: Database,
) : RepositoryTransactionManager {
    override val supportsParallelWrite: Boolean = true

    // a single transaction is only allowed to read and write in one thread (no parallelism)
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun newLimitedDispatcher() = Dispatchers.IO.limitedParallelism(1)
    override suspend fun <T> writeTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[ExposedReadTransaction]?.transaction
        val existingWriteTransaction = coroutineContext[ExposedWriteTransaction]?.transaction
        if (existingReadTransaction != null && existingWriteTransaction != null) block() // just re-use existing transaction (nested)
        else {
            val dispatcher = newLimitedDispatcher()
            newSuspendedTransaction(db = database) {
                withContext(ExposedReadTransaction(this, dispatcher) + ExposedWriteTransaction(this, dispatcher)) {
                    block()
                }
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[ExposedReadTransaction]?.transaction
        if (existingReadTransaction != null) block() // just re-use existing transaction (nested)
        else {
            val dispatcher = newLimitedDispatcher()
            newSuspendedTransaction(db = database) {// currently there is no readonly transaction possible in exposed
                withContext(ExposedReadTransaction(this, dispatcher)) {
                    block()
                }
            }
        }
    }
}