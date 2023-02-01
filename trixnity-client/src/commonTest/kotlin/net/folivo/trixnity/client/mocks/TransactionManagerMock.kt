package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.transaction.TransactionManager

class TransactionManagerMock : TransactionManager {
    override suspend fun withAsyncWriteTransaction(
        onRollback: suspend () -> Unit,
        block: suspend () -> Unit
    ): StateFlow<Boolean>? {
        block()
        return null
    }

    override suspend fun <T> readOperation(block: suspend () -> T): T = block()

    override suspend fun writeOperation(block: suspend () -> Unit) = block()

    override suspend fun writeOperationAsync(key: String, block: suspend () -> Unit): StateFlow<Boolean>? {
        block()
        return null
    }
}