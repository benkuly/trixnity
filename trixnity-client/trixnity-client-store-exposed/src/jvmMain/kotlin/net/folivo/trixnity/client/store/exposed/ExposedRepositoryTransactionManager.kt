package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.coroutines.CoroutineContext

class ExposedRepositoryTransactionManager(
    private val database: Database,
    private val transactionContext: CoroutineContext = Dispatchers.IO
) : RepositoryTransactionManager {
    override suspend fun <T> transaction(block: suspend () -> T): T =
        newSuspendedTransaction(transactionContext, database) { block() }
}