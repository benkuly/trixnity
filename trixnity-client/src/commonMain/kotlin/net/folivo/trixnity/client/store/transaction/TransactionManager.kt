package net.folivo.trixnity.client.store.transaction

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.MatrixClientConfiguration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger { }

interface TransactionManager {
    suspend fun withAsyncWriteTransaction(
        block: suspend () -> Unit
    ): StateFlow<Boolean>?

    suspend fun <T> readOperation(block: suspend () -> T): T

    suspend fun writeOperation(block: suspend () -> Unit)

    /**
     * Saves a write operation into [AsyncTransactionContext] or creates a new Transaction for this operation.
     * This must only be called on writing repository operations directly (save, update, delete).
     *
     * @param key must be unique across all repositories
     * @return true as soon as the transaction has been completed.
     */
    suspend fun writeOperationAsync(key: String, block: suspend () -> Unit): StateFlow<Boolean>?
}

@OptIn(ExperimentalTime::class)
class TransactionManagerImpl(
    private val config: MatrixClientConfiguration,
    private val rtm: RepositoryTransactionManager,
    scope: CoroutineScope,
) : TransactionManager {
    private val asyncTransactions = MutableStateFlow(listOf<AsyncTransaction>())

    init {
        if (config.asyncTransactions) {
            scope.launch {
                try {
                    asyncTransactions.collect { transactions ->
                        transactions.process()
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) withContext(NonCancellable) {
                        log.info { "write all remaining transactions into database before closing scope" }
                        asyncTransactions.value.process()
                        asyncTransactions.value = listOf()
                        log.info { "finished write all remaining transactions into database before closing scope" }
                    }
                    else log.error(throwable) {
                        "AsyncTransactionManager has crashed. " +
                                "If this is seen in the logs and the application is still running, it must be ensured, " +
                                "that the CoroutineScope is managed!"
                    }
                    throw throwable
                }
            }
        }
    }

    private val retrySchedule =
        Schedule.exponential<Throwable>(0.5.seconds)
            .and(Schedule.recurs(3))
            .and(Schedule.doWhile { it !is CancellationException })
            .logInput {
                if (it !is CancellationException) log.warn { "retry failed transaction" }
            }

    private suspend fun List<AsyncTransaction>.process() {
        if (isNotEmpty()) log.trace { "write transactions into database ids=${map { it.id }}" }
        forEach { transaction ->
            retrySchedule.retry {
                // even if the outer scope is cancelled, just finish the current transaction
                withContext(NonCancellable) {
                    // span a large db transaction
                    rtm.writeTransaction {
                        coroutineScope {
                            transaction.operations.forEach { operation ->
                                launch { operation() }
                            }
                        }
                    }
                }
            }
            transaction.transactionHasBeenApplied.value = true
            asyncTransactions.update { it - transaction }
        }
        if (isNotEmpty()) log.trace { "finished write transactions into database ids=${map { it.id }}" }
    }

    override suspend fun withAsyncWriteTransaction(
        block: suspend () -> Unit
    ): StateFlow<Boolean>? =
        if (config.asyncTransactions) {
            val existingTransactionContext = currentCoroutineContext()[AsyncTransactionContext]
            if (existingTransactionContext == null) {
                val newTransactionContext = AsyncTransactionContext()
                log.trace { "create new async transaction id=${newTransactionContext.id}" }
                withContext(newTransactionContext) {
                    block()
                }
                val transaction = newTransactionContext.buildTransaction()
                asyncTransactions.update {
                    it + transaction
                }
                log.trace { "finished async transaction (scheduled for actual processing) id=${newTransactionContext.id}" }
                transaction.transactionHasBeenApplied.also { applied -> applied.first { it } }
            } else {
                log.trace { "use existing async transaction id=${existingTransactionContext.id}" }
                block()
                existingTransactionContext.transactionHasBeenApplied
            }
        } else {
            rtm.writeTransaction(block)
            null
        }

    override suspend fun <T> readOperation(block: suspend () -> T): T = rtm.readTransaction(block)

    override suspend fun writeOperation(block: suspend () -> Unit): Unit = rtm.writeTransaction(block)

    override suspend fun writeOperationAsync(key: String, block: suspend () -> Unit): StateFlow<Boolean>? =
        if (config.asyncTransactions) {
            withAsyncWriteTransaction {
                val transactionContext = currentCoroutineContext()[AsyncTransactionContext]
                checkNotNull(transactionContext)
                transactionContext.addOperation(key) {
                    rtm.writeTransaction(block)
                }
            }
        } else {
            rtm.writeTransaction(block)
            null
        }
}