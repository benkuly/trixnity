package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.flatten
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import org.junit.jupiter.api.fail
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class FallbackKeyIT {

    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 =
            registerAndStartClient("client1", "user1", baseUrl, createExposedRepositoriesModule(newDatabase()))
        startedClient2 = startClient("client2", "user1", baseUrl, createExposedRepositoriesModule(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        runBlocking {
            startedClient1.client.stop()
            startedClient2.client.stop()
        }
    }

    @Test
    fun testFallbackKey(): Unit = runBlocking {
        withTimeout(90_000) {
            val roomId = startedClient1.client.api.rooms.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            startedClient1.client.room.getAll().first { it.size == 1 }
            val startFrom =
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().first().first().eventId
            startedClient1.client.cancelSync(true)


            val oneTimeKeys = startedClient2.claimAllOneTimeKeysFrom(startedClient1)

            withClue("send encrypted message") {
                startedClient2.client.room.sendMessage(roomId) { text("dino") }
                delay(500.milliseconds)
                startedClient2.client.room.getOutbox().flatten().first { outbox -> outbox.all { it.sentAt != null } }
                startedClient2.client.cancelSync(true)
            }

            startedClient1.client.startSync()
            val message =
                startedClient1.client.room.getLastTimelineEvent(roomId).first { it?.first()?.eventId != startFrom }
            message.shouldNotBeNull()
            withTimeoutOrNull(1.seconds) {
                message.first { it.content != null }
            } ?: fail { "could not decrypt event (maybe there was no fallback key)" }
            startedClient1.client.cancelSync(true)

            withClue("ensure, that new one time and fallback keys are generated") {
                startedClient2.claimAllOneTimeKeysFrom(startedClient1) shouldNotContainAnyOf oneTimeKeys
            }
        }
    }

    private suspend fun StartedClient.claimOneTimeKeysFrom(from: StartedClient) =
        client.api.keys.claimKeys(
            mapOf(from.client.userId to mapOf(from.client.deviceId to KeyAlgorithm.SignedCurve25519))
        ).getOrThrow().oneTimeKeys

    private suspend fun StartedClient.claimAllOneTimeKeysFrom(from: StartedClient) =
        (0..101).map { // fails at 75 without fallback key, but just to be sure we ask for more
            withClue("claim one time keys (index=$it)") {
                val oneTimeKeys = claimOneTimeKeysFrom(from)[from.client.userId]
                    ?.get(from.client.deviceId)?.keys
                oneTimeKeys.shouldNotBeNull().shouldHaveSize(1)
                oneTimeKeys.first()
            }
        }
}