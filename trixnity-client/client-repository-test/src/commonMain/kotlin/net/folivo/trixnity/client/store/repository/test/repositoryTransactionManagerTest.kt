package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.UserId
import org.koin.core.Koin
import kotlin.time.Duration.Companion.milliseconds

fun ShouldSpec.repositoryTransactionManagerTest(
    disabledRollbackTest: Boolean,
    disabledSimultaneousReadWriteTests: Boolean,
    customRepositoryTransactionManager: suspend () -> RepositoryTransactionManager?,
    diReceiver: () -> Koin
) {
    lateinit var cut: AccountRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = customRepositoryTransactionManager() ?: di.get()
    }

    suspend fun testWrite(key: Int) {
        cut.save(key.toLong(), Account("", "", UserId("userId"), "", null, null, null, null, null, null, false))
    }

    suspend fun testRead(key: Int) = cut.get(key.toLong())


    should("repositoryTransactionManagerTest: write does not lock") {
        rtm.writeTransaction {
            testWrite(0)
            testRead(0)
            rtm.writeTransaction {
                testWrite(1)
                testRead(1)
            }
        }
    }
    should("repositoryTransactionManagerTest: write allows simultaneous transactions") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        repeat(calls) { i ->
            launch {
                callCount.value++
                callCount.first { it == calls }
                rtm.writeTransaction {
                    testWrite(i)
                }
            }
        }
    }

    val simultaneousWriteTestName = "rrepositoryTransactionManagerTest: write allows simultaneous writes"
    suspend fun simultaneousWriteTest() {
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.value++
                        callCount.first { it == calls }
                        testWrite(i)
                    }
                }
            }
        }
    }
    if (disabledSimultaneousReadWriteTests) xshould(simultaneousWriteTestName) { simultaneousWriteTest() }
    else should(simultaneousWriteTestName) { simultaneousWriteTest() }

    val simultaneousReadTestName = "rrepositoryTransactionManagerTest: write allows simultaneous reads"
    suspend fun simultaneousReadTest() {
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.value++
                        callCount.first { it == calls }
                        testRead(i)
                    }
                }
            }
        }
    }
    if (disabledSimultaneousReadWriteTests) xshould(simultaneousReadTestName) { simultaneousReadTest() }
    else should(simultaneousReadTestName) { simultaneousReadTest() }

    should("repositoryTransactionManagerTest: read does not lock") {
        rtm.readTransaction {
            testRead(0)
            rtm.readTransaction {
                testRead(0)
            }
        }
    }

    should("repositoryTransactionManagerTest: allow work within write transaction") {
        val dummy = MutableStateFlow(listOf<Int>())
        suspend fun work() = coroutineScope {
            repeat(10) { i ->
                launch {
                    delay(10.milliseconds)
                    dummy.update { it + i }
                }
            }
        }
        rtm.writeTransaction {
            testWrite(0)
            work()
            testWrite(1)
        }
        rtm.writeTransaction {
            work()
            testWrite(2)
        }
    }

    should("repositoryTransactionManagerTest: allow work within read transaction") {
        val dummy = MutableStateFlow(listOf<Int>())
        suspend fun work() = coroutineScope {
            repeat(10) { i ->
                launch {
                    delay(10.milliseconds)
                    dummy.update { it + i }
                }
            }
        }
        rtm.writeTransaction {
            testWrite(0)
        }
        rtm.readTransaction {
            testRead(0)
            work()
            testRead(0)
        }
        rtm.readTransaction {
            work()
            testRead(0)
        }
    }

    val rollbackTestName = "repositoryTransactionManagerTest: rollback on exception"
    suspend fun rollbackTest() {
        val thrownException = CancellationException("dino")
        var catchedException: Exception? = null
        try {
            rtm.writeTransaction {
                testWrite(0)
                throw thrownException
            }
        } catch (e: Exception) {
            catchedException = e
        }
        catchedException shouldBe thrownException
        rtm.readTransaction {
            testRead(0)
        } shouldBe null
    }
    if (disabledRollbackTest) xshould(rollbackTestName) { rollbackTest() }
    else should(rollbackTestName) { rollbackTest() }
}