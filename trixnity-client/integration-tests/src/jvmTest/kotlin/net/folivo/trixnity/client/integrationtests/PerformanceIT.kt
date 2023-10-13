package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.createDefaultModules
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.flatten
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Testcontainers
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class PerformanceIT {

    @Container
    val synapseDocker = synapseDocker()

    @Test
    fun realmVsExposed(): Unit = runBlocking {
        withTimeout(120_000) {
            val baseUrl = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapseDocker.host,
                port = synapseDocker.firstMappedPort
            ).build()
            val prepareTestClient =
                registerAndStartClient("client1", "user1", baseUrl, createInMemoryRepositoriesModule())
            val exposedClient = startClient(
                "client2", "user1", baseUrl,
                repositoriesModule = createExposedRepositoriesModule(newDatabase()),
            ) {
                modules = createDefaultModules()
            }
            val realmClient = startClient(
                "client3", "user1", baseUrl,
                repositoriesModule = createRealmRepositoriesModule {
                    inMemory()
                    directory("build/test-db/${uuid4()}")
                },
            ) {
                modules = createDefaultModules()
            }

            exposedClient.client.cancelSync(true)
            realmClient.client.cancelSync(true)
            withContext(Dispatchers.Default.limitedParallelism(20)) {
                repeat(50) {
                    launch {
                        val roomId = prepareTestClient.client.api.rooms.createRoom(
                            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                        ).getOrThrow()
                        repeat(10) { i ->
                            prepareTestClient.client.room.sendMessage(roomId) {
                                text(i.toString())
                            }
                        }
                    }
                }
            }
            prepareTestClient.client.room.getOutbox().flatten().first { outbox -> outbox.all { it.sentAt != null } }
            prepareTestClient.client.stop()
            suspend fun StartedClient.measureSyncProcessing() =
                measureTime {
                    client.syncOnce().getOrThrow()
                }

            val exposedTransactionsTime = exposedClient.measureSyncProcessing()
            val realmTransactionTime = realmClient.measureSyncProcessing()
            val diff = (exposedTransactionsTime / realmTransactionTime) * 100

            println("################################")
            println("exposed transaction: $realmTransactionTime")
            println("################################")
            println("realm transaction: $exposedTransactionsTime")
            println("################################")
            println("diff: ${diff.roundToInt()}%")
            println("################################")

            diff shouldBeLessThan 200.0 // %
            diff shouldBeGreaterThan 50.0 // %
        }
    }
}