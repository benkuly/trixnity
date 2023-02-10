package net.folivo.trixnity.client.store.transaction

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManagerTest {
    private lateinit var cut: TransactionManager
    private lateinit var config: MatrixClientConfiguration
    private lateinit var rtm: RepositoryTransactionManagerMock
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun beforeTest() {
        config = MatrixClientConfiguration()
        rtm = RepositoryTransactionManagerMock()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        cut = TransactionManagerImpl(config, rtm, scope)
    }

    @AfterTest
    fun afterTest() {
        scope.cancel()
    }

    // ############################################
    // withWriteTransaction
    // ############################################
    @Test
    fun shouldCreateNewTransactionAndScheduleIt() = runTest {
        var blockCalled = false
        val block: suspend () -> Unit = { blockCalled = true }
        val result =
            cut.withAsyncWriteTransaction {
                currentCoroutineContext()[AsyncTransactionContext].shouldNotBeNull()
                cut.writeOperationAsync("key", block)
            }
        result.shouldNotBeNull().first { it }

        blockCalled shouldBe true
    }

    @Test
    fun shouldReUseExistingTransactions() = runTest {
        var blockCalled = false
        val block: suspend () -> Unit = { blockCalled = true }
        val transactionContext = AsyncTransactionContext()
        withContext(transactionContext) {
            cut.withAsyncWriteTransaction(block = block) shouldBe transactionContext.transactionHasBeenApplied
        }
        blockCalled shouldBe true
    }

    @Test
    fun shouldRetryOnError() = runTest {
        var blockCalled = MutableStateFlow(0)
        val block: suspend () -> Unit = {
            blockCalled.value++
            throw RuntimeException("unicorn not found")
        }
        val result =
            cut.withAsyncWriteTransaction {
                currentCoroutineContext()[AsyncTransactionContext].shouldNotBeNull()
                cut.writeOperationAsync("key", block)
            }.shouldNotBeNull()

        blockCalled.first { it >= 2 }
        result.value shouldBe false
    }

    @Test
    fun shouldJustDoWriteTransactionWhenAsyncDisabled() = runTest {
        config.asyncTransactions = false
        val block: suspend () -> Unit = {}
        cut.withAsyncWriteTransaction(block = block) shouldBe null
        rtm.writeTransactionCalled.value[0] shouldBe block
    }

    // ############################################
    // readOperation
    // ############################################

    @Test
    fun shouldReadOperationShouldJustDoReadTransaction() = runTest {
        val block: suspend () -> String = { "bla" }
        cut.readOperation(block = block) shouldBe "bla"
        rtm.readTransactionCalled.value[0] shouldBe block
    }

    // ############################################
    // writeOperation
    // ############################################

    @Test
    fun shouldWriteOperationShouldJustDoWriteTransaction() = runTest {
        val block: suspend () -> Unit = {}
        cut.writeOperation(block = block)
        rtm.writeTransactionCalled.value[0] shouldBe block
    }

    // ############################################
    // writeOperationAsync
    // ############################################

    @Test
    fun shouldAddOperationToTransactionAndCapsuleInWriteTransaction() = runTest {
        val block: suspend () -> Unit = {}
        val transactionContext = AsyncTransactionContext()
        withContext(transactionContext) {
            cut.writeOperationAsync("key") {/* other block */ }
            // should overwrite previous operation with same key
            cut.writeOperationAsync("key", block)
        }
        val transaction = transactionContext.buildTransaction()
        transaction.operations shouldHaveSize 1
        transaction.operations shouldNotContain block
        transaction.operations[0].invoke()
        rtm.writeTransactionCalled.value[0] shouldBe block
    }

    @Test
    fun shouldJustDoWriteTransactionWhenAsyncDisabled2() = runTest {
        config.asyncTransactions = false
        val block: suspend () -> Unit = {}
        cut.writeOperation(block = block)
        rtm.writeTransactionCalled.value[0] shouldBe block
    }
}