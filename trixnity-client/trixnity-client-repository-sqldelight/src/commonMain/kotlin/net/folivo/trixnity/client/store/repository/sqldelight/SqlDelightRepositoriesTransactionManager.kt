package net.folivo.trixnity.client.store.repository.sqldelight

import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.sqldelight.db.Database
import kotlin.coroutines.CoroutineContext

class SqlDelightRepositoriesTransactionManager(
    private val db: Database,
    private val blockingTransactionCoroutineContext: CoroutineContext,
) : RepositoryTransactionManager {
    /**
     * This implementation is very hacky. SqlDelight only allows transactions of thread-blocking code.
     * Because we don't do super heavy stuff within a transaction this should not affect the performance very much.
     */
    override suspend fun <T> transaction(block: suspend () -> T): T =
        callRunBlocking(blockingTransactionCoroutineContext) {
            db.transactionWithResult {
                callRunBlocking {
                    block()
                }
            }
        }
}