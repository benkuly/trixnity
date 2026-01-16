package de.connect2x.trixnity.client.integrationtests

import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
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
    private lateinit var startedClient3: StartedClient

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
            registerAndStartClient("client1", "user1", baseUrl, RepositoriesModule.exposed(newDatabase()))
        startedClient2 = startClient("client2", "user1", baseUrl, RepositoriesModule.exposed(newDatabase()))
        startedClient3 = startClient("client3", "user1", baseUrl, RepositoriesModule.exposed(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
        startedClient3.client.close()
    }

    @Test
    fun testFallbackKey(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(90_000) {
            val roomId = startedClient1.client.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            startedClient1.client.room.getAll().firstWithTimeout { it.size == 1 }
            val startFrom =
                startedClient1.client.room.getLastTimelineEvent(roomId).filterNotNull().firstWithTimeout()
                    .first().eventId
            startedClient1.client.cancelSync()


            val oneTimeKeys = startedClient2.claimAllOneTimeKeysFrom(startedClient1)

            withCluePrintln("send encrypted message") {
                startedClient2.client.room.sendMessage(roomId) { text("dino") } // uses fallback key
                delay(500.milliseconds)
                startedClient2.client.room.waitForOutboxSent()
                startedClient2.client.cancelSync()

                startedClient3.client.room.sendMessage(roomId) { text("dino") } // uses fallback key
                delay(500.milliseconds)
                startedClient3.client.room.waitForOutboxSent()
                startedClient3.client.cancelSync()
            }

            startedClient1.client.startSync()
            val message =
                startedClient1.client.room.getLastTimelineEvent(roomId).firstWithTimeout {
                    val eventId = it?.firstWithTimeout()?.eventId
                    eventId != null && eventId != startFrom
                }
            message.shouldNotBeNull()
            withTimeoutOrNull(1.seconds) {
                message.firstWithTimeout { it.content != null }
            } ?: fail { "could not decrypt event (maybe there was no fallback key)" }
            startedClient1.client.stopSync()

            withCluePrintln("ensure, that new one time and fallback keys are generated") {
                startedClient2.claimAllOneTimeKeysFrom(startedClient1)
                    .shouldHaveAtLeastSize(30)
                    .shouldNotContainAnyOf(oneTimeKeys)
            }
        }
    }

    private suspend fun StartedClient.claimOneTimeKeysFrom(from: StartedClient) =
        client.api.key.claimKeys(
            mapOf(from.client.userId to mapOf(from.client.deviceId to KeyAlgorithm.SignedCurve25519))
        ).getOrThrow().oneTimeKeys

    private suspend fun StartedClient.claimAllOneTimeKeysFrom(from: StartedClient) =
        (0..101).map { // fails at 75 without fallback key, but just to be sure we ask for more
            withCluePrintln("claim one time keys (index=$it)") {
                val oneTimeKeys = claimOneTimeKeysFrom(from)[from.client.userId]
                    ?.get(from.client.deviceId)?.keys
                oneTimeKeys.shouldNotBeNull().shouldHaveSize(1)
                oneTimeKeys.first()
            }
        }
}