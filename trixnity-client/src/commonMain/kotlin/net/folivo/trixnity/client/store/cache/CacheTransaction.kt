package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import net.folivo.trixnity.utils.concurrentMutableList
import kotlin.coroutines.CoroutineContext

private val log = KotlinLogging.logger("net.folivo.trixnity.client.store.cache.CacheTransaction")

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
        try {
            block(newCacheTransaction)
            val onCommitActions = newCacheTransaction.onCommitActions.read { toList() }
            if (onCommitActions.isNotEmpty()) {
                log.trace { "apply commit actions for transaction" }
                onCommitActions.forEach { it() }
            }
        } catch (exception: Exception) {
            withContext(NonCancellable) {
                val onRollbackActions = newCacheTransaction.onRollbackActions.read { reversed() }
                if (onRollbackActions.isNotEmpty()) {
                    log.debug { "apply rollback actions for transaction" }
                    onRollbackActions.forEach { it() }
                }
            }
            throw exception
        }
    }
}