package net.folivo.trixnity.client.store.transaction

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class AsyncTransactionContext : CoroutineContext.Element {
    override val key: CoroutineContext.Key<AsyncTransactionContext> = Key

    companion object Key : CoroutineContext.Key<AsyncTransactionContext>

    val id = uuid4().toString()
    private val _transactionHasBeenApplied = MutableStateFlow(false)
    val transactionHasBeenApplied = _transactionHasBeenApplied.asStateFlow()

    private val mutex = Mutex()
    private val operations = mutableMapOf<String, suspend () -> Unit>()

    suspend fun addOperation(key: String, operation: suspend () -> Unit) = mutex.withLock {
        operations.put(key, operation)
    }

    fun buildTransaction(onRollback: suspend () -> Unit) =
        AsyncTransaction(
            id = id,
            operations = operations.values.toList(),
            transactionHasBeenApplied = _transactionHasBeenApplied,
            onRollback = onRollback,
        )
}