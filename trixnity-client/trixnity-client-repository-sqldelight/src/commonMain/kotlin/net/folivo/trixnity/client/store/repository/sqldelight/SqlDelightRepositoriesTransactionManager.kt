package net.folivo.trixnity.client.store.repository.sqldelight

import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class SqlDelightRepositoriesTransactionManager(
    private val db: Database,
    private val blockingTransactionCoroutineContext: CoroutineContext,
) : RepositoryTransactionManager {
    override suspend fun <T> readTransaction(block: suspend () -> T): T =
        callRunBlocking(blockingTransactionCoroutineContext) {
            db.transactionWithResult {
                callRunBlocking(blockingTransactionCoroutineContext) {
                    block()
                }
            }
        }

    override suspend fun writeTransaction(block: suspend () -> Unit) =
        callRunBlocking(blockingTransactionCoroutineContext) {
            db.transactionWithResult {
                callRunBlocking(blockingTransactionCoroutineContext) {
                    block()
                }
            }
        }
}