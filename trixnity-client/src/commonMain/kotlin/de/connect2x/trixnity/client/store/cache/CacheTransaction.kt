package de.connect2x.trixnity.client.store.cache

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import de.connect2x.trixnity.utils.concurrentMutableList
import kotlin.coroutines.CoroutineContext

private val log = Logger("de.connect2x.trixnity.client.store.cache.CacheTransaction")

internal class CacheTransaction : CoroutineContext.Element {
    val onCommitActions = concurrentMutableList<suspend () -> Unit>()
    val onRollbackActions = concurrentMutableList<suspend () -> Unit>()

    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<CacheTransaction>
}

internal suspend fun withCacheTransaction(block: suspend (CacheTransaction) -> Unit) {
    val existingCacheTransaction = currentCoroutineContext()[CacheTransaction]
    if (existingCacheTransaction != null) block(existingCacheTransaction)
    else {
        val newCacheTransaction = CacheTransaction()
        withContext(NonCancellable) { // prevent that the store and cache get out of sync on a CancellationException
            try {
                block(newCacheTransaction)
                val onCommitActions = newCacheTransaction.onCommitActions.read { toList() }
                if (onCommitActions.isNotEmpty()) {
                    log.trace { "apply commit actions for transaction" }
                    onCommitActions.forEach { it() }
                }
            } catch (exception: Exception) {
                val onRollbackActions = newCacheTransaction.onRollbackActions.read { reversed() }
                if (onRollbackActions.isNotEmpty()) {
                    log.debug { "apply rollback actions for transaction due to $exception" }
                    onRollbackActions.forEach { it() }
                }
                throw exception
            }
        }
    }
}