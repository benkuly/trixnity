package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.comparables.shouldBeLessThan
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.createDefaultModules
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import org.koin.dsl.module
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Testcontainers
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class AsyncTransactionPerformanceIT {

    private lateinit var startedClient1: StartedClient
    private lateinit var startedClientAsyncTransactions: StartedClient
    private lateinit var startedClientReference: StartedClient

    @Container
    val synapseDocker = synapseDocker()

    private val delayedRepositoryTransactionManagerModule = module {
        single<RepositoryTransactionManager> {
            object : RepositoryTransactionManager {
                override suspend fun <T> readTransaction(block: suspend () -> T): T {
                    delay(5.milliseconds)
                    return block()
                }

                override suspend fun writeTransaction(block: suspend () -> Unit) {
                    delay(10.milliseconds)
                    block()
                }
            }
        }
    }

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 = registerAndStartClient("client1", "user1", baseUrl, createInMemoryRepositoriesModule())
        startedClientAsyncTransactions = startClient(
            "client2", "user1", baseUrl,
            repositoriesModule = createInMemoryRepositoriesModule(),
        ) {
            asyncTransactions = true
            modules = createDefaultModules() + delayedRepositoryTransactionManagerModule
        }
        startedClientReference = startClient(
            "client3", "user1", baseUrl,
            repositoriesModule = createInMemoryRepositoriesModule(),
        ) {
            asyncTransactions = false
            modules = createDefaultModules() + delayedRepositoryTransactionManagerModule
        }
    }

    @AfterTest
    fun afterEach() {
        startedClient1.scope.cancel()
    }

    @Test
    fun testAsyncTransactionPerformance(): Unit = runBlocking {
        withTimeout(120_000) {
            startedClientAsyncTransactions.client.cancelSync(true)
            startedClientReference.client.cancelSync(true)
            withContext(Dispatchers.Default.limitedParallelism(20)) {
                repeat(50) {
                    launch {
                        val roomId = startedClient1.client.api.rooms.createRoom(
                            initialState = listOf(Event.InitialStateEvent(content = EncryptionEventContent(), ""))
                        ).getOrThrow()
                        repeat(10) { i ->
                            startedClient1.client.room.sendMessage(roomId) {
                                text(i.toString())
                            }
                        }
                    }
                }
            }
            startedClient1.client.room.getOutbox().first { outbox -> outbox.all { it.sentAt != null } }
            startedClient1.client.cancelSync(true)
            suspend fun StartedClient.measureSyncProcessing() =
                measureTime {
                    client.syncOnce().getOrThrow()
                }

            val asyncTransactionsTime = startedClientAsyncTransactions.measureSyncProcessing()
            val referenceTime = startedClientReference.measureSyncProcessing()
            val diff = (asyncTransactionsTime / referenceTime) * 100

            println("################################")
            println("reference transaction: $referenceTime")
            println("################################")
            println("async transaction: $asyncTransactionsTime")
            println("################################")
            println("diff: ${diff.roundToInt()}%")
            println("################################")

            diff shouldBeLessThan 50.0
        }
    }
}