package de.connect2x.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager

class RepositoryTransactionManagerMock : RepositoryTransactionManager {
    val readTransactionCalled = MutableStateFlow(listOf<suspend () -> Any?>())
    override suspend fun <T> readTransaction(block: suspend () -> T): T {
        val result = block()
        readTransactionCalled.update { it + block }
        return result
    }

    val writeTransactionCalled = MutableStateFlow(listOf<suspend () -> Unit>())

    override suspend fun writeTransaction(block: suspend () -> Unit) {
        block()
        writeTransactionCalled.update { it + block }
    }
}