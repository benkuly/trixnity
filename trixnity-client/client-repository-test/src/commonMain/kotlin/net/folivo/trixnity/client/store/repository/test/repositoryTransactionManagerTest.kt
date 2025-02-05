package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

fun ShouldSpec.repositoryTransactionManagerTest(
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
        cut.save(
            key.toLong(), Account(
                olmPickleKey = "",
                baseUrl = "",
                userId = UserId("userId"),
                deviceId = "",
                accessToken = null,
                refreshToken = null,
                syncBatchToken = null,
                filterId = null,
                backgroundFilterId = null,
                displayName = null,
                avatarUrl = null,
                isLocked = false
            )
        )
    }

    suspend fun testRead(key: Int) = cut.get(key.toLong())


    should("repositoryTransactionManagerTest: write does not lock") {
        rtm.writeTransaction {
            testWrite(0)
            testRead(0)
            rtm.writeTransaction {
                testWrite(1)
                testRead(1)
                rtm.writeTransaction {
                    testWrite(2)
                    testRead(2)
                }
            }
        }
    }
    should("repositoryTransactionManagerTest: write allows simultaneous transactions") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        repeat(calls) { i ->
            launch {
                callCount.update { it + 1 }
                callCount.first { it == calls }
                rtm.writeTransaction {
                    testWrite(i)
                }
            }
        }
    }

    should("repositoryTransactionManagerTest: write allows simultaneous writes") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        testWrite(i)
                    }
                }
            }
        }
    }

    should("repositoryTransactionManagerTest: write allows simultaneous reads") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        testRead(i)
                    }
                }
            }
        }
    }

    should("repositoryTransactionManagerTest: read does not lock") {
        rtm.readTransaction {
            testRead(0)
            rtm.readTransaction {
                testRead(0)
                rtm.readTransaction {
                    testRead(0)
                }
            }
        }
    }

    should("repositoryTransactionManagerTest: allow read in write transaction") {
        rtm.writeTransaction {
            testRead(0)
        }
    }

    should("repositoryTransactionManagerTest: allow read in parallel to write transaction") {
        val startWrite = MutableStateFlow(false)
        val finishedRead = MutableStateFlow(false)
        launch {
            rtm.writeTransaction {
                startWrite.value = true
                finishedRead.first { it }
                testWrite(0)
            }
        }
        startWrite.first { it }
        rtm.readTransaction {
            testRead(0)
        }
        finishedRead.value = true
    }

    should("repositoryTransactionManagerTest: read allows simultaneous reads") {
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.readTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        testRead(i)
                    }
                }
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

    should("repositoryTransactionManagerTest: rollback on exception") {
        val thrownException = CancellationException("dino")
        var caughtException: Exception? = null
        try {
            rtm.writeTransaction {
                testWrite(0)
                throw thrownException
            }
        } catch (e: Exception) {
            caughtException = e
        }
        caughtException shouldBe thrownException
        rtm.readTransaction {
            testRead(0)
        } shouldBe null
    }
}