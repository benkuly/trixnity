package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.ObjectStore
import com.juul.indexeddb.Transaction
import com.juul.indexeddb.WriteTransaction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class IndexedDBReadTransaction(
    val transaction: Transaction,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<IndexedDBReadTransaction>
}

class IndexedDBWriteTransaction(
    val transaction: WriteTransaction,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<IndexedDBWriteTransaction>
}

suspend fun <T> IndexedDBRepository.withIndexedDBRead(block: suspend Transaction.(ObjectStore) -> T) =
    coroutineScope {
        val exposedReadTransaction = checkNotNull(coroutineContext[IndexedDBReadTransaction])
        exposedReadTransaction.transaction.run {
            block(objectStore(objectStoreName))
        }
    }

suspend fun <T> IndexedDBRepository.withIndexedDBWrite(block: suspend WriteTransaction.(ObjectStore) -> T) =
    coroutineScope {
        val exposedWriteTransaction = checkNotNull(coroutineContext[IndexedDBWriteTransaction])
        exposedWriteTransaction.transaction.run {
            block(objectStore(objectStoreName))
        }
    }

class IndexedDBRepositoryTransactionManager(
    private val database: Database,
    private val allObjectStores: Array<String>,
) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit): Unit = coroutineScope {
        val existingReadTransaction = coroutineContext[IndexedDBReadTransaction]?.transaction
        val existingWriteTransaction = coroutineContext[IndexedDBWriteTransaction]?.transaction
        if (existingReadTransaction != null && existingWriteTransaction != null) block() // just re-use existing transaction (nested)
        else {
            database.writeTransaction(*allObjectStores) {
                withContext(IndexedDBReadTransaction(this) + IndexedDBWriteTransaction(this)) {
                    block()
                }
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[IndexedDBReadTransaction]?.transaction
        if (existingReadTransaction != null) block() // just re-use existing transaction (nested)
        else {
            database.transaction(*allObjectStores) {
                withContext(IndexedDBReadTransaction(this)) {
                    block()
                }
            }
        }
    }
}