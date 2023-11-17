package net.folivo.trixnity.client.integrationtests

import com.benasher44.uuid.uuid4
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.repository.realm.createRealmRepositoriesModule
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.user.canSendEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class DatabaseIT {

    private suspend fun test(matrixClient: MatrixClient) = withTimeout(30_000) {
        val roomId = matrixClient.api.room.createRoom(
            initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
        ).getOrThrow()
        matrixClient.room.getById(roomId).filterNotNull().first()
        delay(5.seconds) // wait for cache to be cleared
        withClue("get state m.room.create") {
            matrixClient.room.getState<CreateEventContent>(roomId, "").filterNotNull().first()
        }
        withClue("check can send event") {
            matrixClient.user.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe true
        }
    }

    @Test
    fun inMemory(): Unit = runBlocking {
        val synapse = synapseDocker().also { it.start() }
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapse.host,
            port = synapse.firstMappedPort
        ).build()
        val matrixClient =
            registerAndStartClient("client", "client", baseUrl, createInMemoryRepositoriesModule()) {
                cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(100.milliseconds)
            }.client
        test(matrixClient)
        matrixClient.stop()
        synapse.stop()
    }

    @Test
    fun realm(): Unit = runBlocking {
        val synapse = synapseDocker().also { it.start() }
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapse.host,
            port = synapse.firstMappedPort
        ).build()
        val matrixClient =
            registerAndStartClient("client", "client", baseUrl, createRealmRepositoriesModule {
                inMemory()
                directory("build/test-db/${uuid4()}")
            }) {
                cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(100.milliseconds)
            }.client
        test(matrixClient)
        matrixClient.stop()
        synapse.stop()
    }

    @Test
    fun exposed(): Unit = runBlocking {
        val synapse = synapseDocker().also { it.start() }
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapse.host,
            port = synapse.firstMappedPort
        ).build()
        val matrixClient =
            registerAndStartClient("client", "client", baseUrl, createExposedRepositoriesModule(newDatabase())) {
                cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(100.milliseconds)
            }.client
        test(matrixClient)
        matrixClient.stop()
        synapse.stop()
    }
}