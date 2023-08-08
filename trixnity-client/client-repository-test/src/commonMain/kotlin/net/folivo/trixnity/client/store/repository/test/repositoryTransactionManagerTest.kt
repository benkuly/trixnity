package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.koin.core.Koin

fun ShouldSpec.repositoryTransactionManagerTest(disabledRollbackTest: Boolean, diReceiver: () -> Koin) {
    lateinit var cut: AccountRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }

    suspend fun testWrite(key: Int) {
        cut.save(key.toLong(), Account(null, null, null, null, null, null, null, null, null, null))
    }

    suspend fun testRead(key: Int) = cut.get(key.toLong())


    should("repositoryTransactionManagerTest: write not lock") {
        rtm.writeTransaction {
            testWrite(0)
            testRead(0)
            rtm.writeTransaction {
                testWrite(1)
                testRead(1)
            }
        }
    }
    should("repositoryTransactionManagerTest: write allow simultaneous transactions") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        repeat(calls) { i ->
            launch {
                callCount.value++
                rtm.writeTransaction {
                    callCount.first { it == calls }
                    testWrite(i)
                }
            }
        }
    }
    should("repositoryTransactionManagerTest: write allow simultaneous writes") {
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

    should("repositoryTransactionManagerTest: read not lock") {
        rtm.readTransaction {
            testRead(0)
            rtm.readTransaction {
                testRead(0)
            }
        }
    }

    if (disabledRollbackTest.not()) {
        should("repositoryTransactionManagerTest: rollback on exception") {
            try {
                rtm.writeTransaction {
                    testWrite(0)
                    throw CancellationException("dino")
                }
            } catch (_: Exception) {
            }
            rtm.readTransaction {
                testRead(0)
            } shouldBe null
        }
    }
}