package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient.LoginState
import net.folivo.trixnity.client.MatrixClient.LoginState.LOGGED_IN
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType.User
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest.Password
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test

@Testcontainers
class LogoutIT {

    @Test
    fun shouldLogoutOnDeviceDeletion(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val synapseDocker = synapseDocker()
            synapseDocker.start()

            fun getBaseUrl() = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapseDocker.host,
                port = synapseDocker.firstMappedPort
            ).build()

            val startedClient1 = registerAndStartClient(
                "client1", "user1", getBaseUrl(),
                createExposedRepositoriesModule(newDatabase())
            )
            val startedClient2 =
                startClient("client2", "user1", getBaseUrl(), createExposedRepositoriesModule(newDatabase()))

            withClue("check client2 is logged in and sync is running") {
                withTimeout(30_000) {
                    startedClient2.client.syncState.first { it == SyncState.RUNNING }
                    startedClient2.client.loginState.first { it == LOGGED_IN }
                }
            }

            val deleteStep = startedClient1.client.api.device.deleteDevice("client2").getOrThrow()
            deleteStep.shouldBeInstanceOf<UIA.Step<Unit>>()
                .authenticate(Password(User("user1"), startedClient1.password)).getOrThrow()
                .shouldBeInstanceOf<UIA.Success<Unit>>()

            withClue("check client2 is logged out and sync is stopped") {
                withTimeout(30_000) {
                    startedClient2.client.syncState.first { it == SyncState.STOPPED }
                    startedClient2.client.loginState.first { it == LoginState.LOGGED_OUT }
                }
            }

            startedClient1.client.close()
            startedClient2.client.close()
            synapseDocker.stop()
        }
    }

    @Test
    fun shouldNotLogoutOnDMassiveRequests(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(60_000) {
            val synapse = synapseDocker()
                .apply {
                    withEnv(mapOf("SYNAPSE_CONFIG_PATH" to "/data/homeserver-logout.yaml"))
                    start()
                }
            val baseUrl = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapse.host,
                port = synapse.firstMappedPort
            ).build()
            val startedClient = registerAndStartClient(
                "client1", "user1", baseUrl,
                createExposedRepositoriesModule(newDatabase())
            )
            startedClient.client.syncState.first { it == SyncState.RUNNING }
            startedClient.client.loginState.value shouldBe LOGGED_IN

            val room = startedClient.client.api.room.createRoom().getOrThrow()
            repeat(200) { i ->
                coroutineScope {
                    repeat(20) {
                        launch {
                            startedClient.client.api.room.getMembers(room).getOrThrow()
                        }
                    }
                    repeat(20) {
                        launch {
                            startedClient.client.api.user.getPresence(startedClient.client.userId).getOrThrow()
                        }
                    }
                    repeat(10) {
                        launch {
                            startedClient.client.api.room.sendMessageEvent(
                                room,
                                RoomMessageEventContent.TextBased.Text(i.toString())
                            ).getOrThrow()
                        }
                    }
                }
            }
            startedClient.client.loginState.value shouldBe LOGGED_IN
            startedClient.client.close()
            synapse.stop()
        }
    }
}