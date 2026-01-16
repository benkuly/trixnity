package de.connect2x.trixnity.client.store.repository.room

import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class RoomReadTransaction(
    val mutex: Mutex,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RoomReadTransaction>
}

class RoomWriteTransaction(
    val mutex: Mutex,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RoomWriteTransaction>
}

suspend inline fun <T> withRoomRead(crossinline block: suspend () -> T): T = coroutineScope {
    val exposedReadTransaction =
        checkNotNull(coroutineContext[RoomReadTransaction]) { "read transaction is missing" }
    exposedReadTransaction.mutex.withLock {
        block()
    }
}

suspend inline fun <T> withRoomWrite(crossinline block: suspend () -> T): Unit = coroutineScope {
    val exposedWriteTransaction =
        checkNotNull(coroutineContext[RoomWriteTransaction]) { "write transaction is missing" }
    exposedWriteTransaction.mutex.withLock {
        block()
    }
}

class RoomRepositoryTransactionManager(
    private val db: TrixnityRoomDatabase
) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit) = coroutineScope {
        val existingWriteTransaction = coroutineContext[RoomWriteTransaction]
        if (existingWriteTransaction != null) block()
        else {
            val mutex = Mutex()

            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    withContext(RoomReadTransaction(mutex) + RoomWriteTransaction(mutex)) {
                        block()
                    }
                }
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingReadTransaction = coroutineContext[RoomReadTransaction]
        if (existingReadTransaction != null) block()
        else {
            db.useReaderConnection { transactor ->
                withContext(RoomReadTransaction(Mutex())) {
                    block()
                }
            }
        }
    }
}