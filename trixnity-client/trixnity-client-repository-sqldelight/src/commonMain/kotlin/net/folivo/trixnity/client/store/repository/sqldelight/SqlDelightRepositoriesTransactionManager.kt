package net.folivo.trixnity.client.store.repository.sqldelight

import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.sqldelight.db.Database
import kotlin.coroutines.CoroutineContext

class SqlDelightRepositoriesTransactionManager(
    private val db: Database,
    private val blockingTransactionCoroutineContext: CoroutineContext,
) : RepositoryTransactionManager {
    override val supportsParallelWrite: Boolean = true
    override suspend fun <T> readTransaction(block: suspend () -> T): T =
        callRunBlocking(blockingTransactionCoroutineContext) {
            db.transactionWithResult {
                callRunBlocking(blockingTransactionCoroutineContext) {
                    block()
                }
            }
        }

    override suspend fun <T> writeTransaction(block: suspend () -> T): T =
        callRunBlocking(blockingTransactionCoroutineContext) {
            db.transactionWithResult {
                callRunBlocking(blockingTransactionCoroutineContext) {
                    block()
                }
            }
        }
}