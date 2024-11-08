package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.ObjectStore
import com.juul.indexeddb.Transaction
import com.juul.indexeddb.WriteTransaction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class IndexedDBReadTransaction(
    val database: Database,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<IndexedDBReadTransaction>
}

class IndexedDbWriteOperation(
    val objectStoreName: String,
    val operation: suspend WriteTransaction.(ObjectStore) -> Unit,
)

class IndexedDBWriteTransaction(
    val database: Database,
    val testMode: Boolean,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<IndexedDBWriteTransaction>

    private val operations = mutableListOf<IndexedDbWriteOperation>()
    private val mutex = Mutex()

    suspend fun addOperation(objectStoreName: String, operation: suspend WriteTransaction.(ObjectStore) -> Unit): Unit =
        mutex.withLock {
            operations.add(IndexedDbWriteOperation(objectStoreName, operation))
        }

    fun getOperations(): List<IndexedDbWriteOperation> = operations
}

suspend fun <T> IndexedDBRepository.withIndexedDBRead(block: suspend Transaction.(ObjectStore) -> T) =
    coroutineScope {
        val readTransaction =
            checkNotNull(coroutineContext[IndexedDBReadTransaction]) { "read transaction is missing" }
        readTransaction.database.transaction(objectStoreName) {
            block(objectStore(objectStoreName))
        }
    }

suspend fun IndexedDBRepository.withIndexedDBWrite(block: suspend WriteTransaction.(ObjectStore) -> Unit): Unit =
    coroutineScope {
        val writeTransaction =
            checkNotNull(coroutineContext[IndexedDBWriteTransaction]) { "write transaction is missing" }
        if (writeTransaction.testMode)
            writeTransaction.database.writeTransaction(objectStoreName) {
                block(objectStore(objectStoreName))
            }
        else writeTransaction.addOperation(objectStoreName, block)
    }

class IndexedDBRepositoryTransactionManager(
    private val database: Database,
    private val allObjectStores: Array<String>,
    private val testMode: Boolean = false,
) : RepositoryTransactionManager {
    // we only allow one write transaction at a time to prevent inconsistency
    private val mutex = Mutex()

    override suspend fun writeTransaction(block: suspend () -> Unit) {
        coroutineScope {
            val existingReadTransaction = coroutineContext[IndexedDBReadTransaction]
            val existingWriteTransaction = coroutineContext[IndexedDBWriteTransaction]
            if (existingReadTransaction != null && existingWriteTransaction != null) block() // just reuse existing transaction (nested)
            else mutex.withLock {
                val writeTransaction = IndexedDBWriteTransaction(database, testMode)
                withContext(IndexedDBReadTransaction(database) + writeTransaction) {
                    block()
                }
                if (testMode.not()) {
                    // we collect all operations, because IndeedDB don't allow doing async work within a transaction
                    val operations = writeTransaction.getOperations()
                    database.writeTransaction(*allObjectStores) {
                        operations.forEach { operation ->
                            operation.operation.invoke(this, objectStore(operation.objectStoreName))
                        }
                    }
                }
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[IndexedDBReadTransaction]
        if (existingReadTransaction != null) block()
        else {
            // we do not actually create a read transaction, because each operation creates its own
            withContext(IndexedDBReadTransaction(database)) {
                block()
            }
        }
    }
}