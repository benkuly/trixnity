package net.folivo.trixnity.client.store.transaction

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock

class TransactionManagerTest : ShouldSpec({
    lateinit var cut: TransactionManager
    lateinit var config: MatrixClientConfiguration
    lateinit var rtm: RepositoryTransactionManagerMock
    lateinit var scope: CoroutineScope

    beforeTest {
        config = MatrixClientConfiguration()
        rtm = RepositoryTransactionManagerMock()
        scope = CoroutineScope(Dispatchers.Default)
        cut = TransactionManagerImpl(config, rtm, scope)
    }
    afterTest { scope.cancel() }

    context(TransactionManager::withWriteTransaction.name) {
        context("async transactions enabled on withWriteTransaction") {
            should("create new transaction and schedule") {
                var onRollbackCalled = false
                val onRollback: suspend () -> Unit = { onRollbackCalled = true }
                var blockCalled = false
                val block: suspend () -> Unit = { blockCalled = true }
                val result =
                    cut.withWriteTransaction(onRollback) {
                        currentCoroutineContext()[AsyncTransactionContext].shouldNotBeNull()
                        cut.writeOperationAsync("key", block)
                    }
                result.shouldNotBeNull().first { it }

                onRollbackCalled shouldBe false
                blockCalled shouldBe true
            }
            should("re-use existing transaction") {
                var blockCalled = false
                val block: suspend () -> Unit = { blockCalled = true }
                val transactionContext = AsyncTransactionContext()
                withContext(transactionContext) {
                    cut.withWriteTransaction(block = block) shouldBe transactionContext.transactionHasBeenApplied
                }
                blockCalled shouldBe true
            }
            should("retry and call rollback on error") {
                val onRollbackCalled = MutableStateFlow(false)
                val onRollback: suspend () -> Unit = { onRollbackCalled.value = true }
                var blockCalled = 0
                val block: suspend () -> Unit = {
                    blockCalled++
                    throw RuntimeException("unicorn not found")
                }
                val result =
                    cut.withWriteTransaction(onRollback) {
                        currentCoroutineContext()[AsyncTransactionContext].shouldNotBeNull()
                        cut.writeOperationAsync("key", block)
                    }.shouldNotBeNull()

                onRollbackCalled.first { it }
                result.value shouldBe false
                blockCalled shouldBeGreaterThan 2
            }
        }
        context("async transactions disabled on withWriteTransaction") {
            beforeTest { config.enableAsyncTransactions = false }
            should("just do write transaction") {
                val block: suspend () -> Unit = {}
                cut.withWriteTransaction(block = block) shouldBe null
                rtm.writeTransactionCalled.value[0] shouldBe block
            }
        }
    }
    context("readOperation") {
        should("just do read transaction") {
            val block: suspend () -> String = { "bla" }
            cut.readOperation(block = block) shouldBe "bla"
            rtm.readTransactionCalled.value[0] shouldBe block
        }
    }
    context("writeOperation") {
        should("just do write transaction") {
            val block: suspend () -> Unit = {}
            cut.writeOperation(block = block)
            rtm.writeTransactionCalled.value[0] shouldBe block
        }
    }
    context(TransactionManager::writeOperationAsync.name) {
        context("async transactions enabled on writeOperationAsync") {
            should("add operation to transaction and capsule in write transaction") {
                val block: suspend () -> Unit = {}
                val transactionContext = AsyncTransactionContext()
                withContext(transactionContext) {
                    cut.writeOperationAsync("key") {/* other block */ }
                    // should overwrite previous operation with same key
                    cut.writeOperationAsync("key", block)
                }
                val transaction = transactionContext.buildTransaction { }
                transaction.operations shouldHaveSize 1
                transaction.operations shouldNotContain block
                transaction.operations[0].invoke()
                rtm.writeTransactionCalled.value[0] shouldBe block
            }
        }
        context("async transactions disabled on writeOperationAsync") {
            beforeTest { config.enableAsyncTransactions = false }
            should("just do write transaction") {
                val block: suspend () -> Unit = {}
                cut.writeOperation(block = block)
                rtm.writeTransactionCalled.value[0] shouldBe block
            }
        }
    }
})