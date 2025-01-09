package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import kotlin.coroutines.CoroutineContext

class ExposedReadTransaction(
    val transaction: Transaction,
    val mutex: Mutex,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ExposedReadTransaction>
}

class ExposedWriteTransaction(
    val transaction: Transaction,
    val mutex: Mutex,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ExposedWriteTransaction>
}

suspend fun <T> withExposedRead(block: () -> T): T = coroutineScope {
    val exposedReadTransaction =
        checkNotNull(coroutineContext[ExposedReadTransaction]) { "read transaction is missing" }
    exposedReadTransaction.mutex.withLock {
        exposedReadTransaction.transaction.withSuspendTransaction { block() }
    }
}

suspend fun <T> withExposedWrite(block: () -> T): Unit = coroutineScope {
    val exposedWriteTransaction =
        checkNotNull(coroutineContext[ExposedWriteTransaction]) { "write transaction is missing" }
    exposedWriteTransaction.mutex.withLock {
        exposedWriteTransaction.transaction.withSuspendTransaction { block() }
    }
}

class ExposedRepositoryTransactionManager(private val database: Database) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit) = coroutineScope {
        val existingWriteTransaction = coroutineContext[ExposedWriteTransaction]?.transaction
        if (existingWriteTransaction != null) block() // just reuse existing transaction (nested)
        else {
            val mutex = Mutex()
            newSuspendedTransaction(db = database) {
                withContext(ExposedReadTransaction(this, mutex) + ExposedWriteTransaction(this, mutex)) {
                    block()
                }
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[ExposedReadTransaction]?.transaction
        if (existingReadTransaction != null) block() // just reuse existing transaction (nested)
        else {
            newSuspendedTransaction(db = database) { // currently there is no readonly transaction possible in exposed
                withContext(ExposedReadTransaction(this, Mutex())) {
                    block()
                }
            }
        }
    }
}