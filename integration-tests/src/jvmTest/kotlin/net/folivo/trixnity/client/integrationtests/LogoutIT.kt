package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.MatrixClient.LoginState.LOGGED_IN
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType.User
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest.Password
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test

@Testcontainers
class LogoutIT {

    @Container
    val synapseDocker = GenericContainer<Nothing>(DockerImageName.parse("matrixdotorg/synapse:$synapseVersion"))
        .apply {
            withEnv(
                mapOf(
                    "VIRTUAL_HOST" to "localhost",
                    "VIRTUAL_PORT" to "8008",
                    "SYNAPSE_SERVER_NAME" to "localhost",
                    "SYNAPSE_REPORT_STATS" to "no",
                    "UID" to "1000",
                    "GID" to "1000"
                )
            )
            withClasspathResourceMapping("data", "/data", BindMode.READ_WRITE)
            withExposedPorts(8008)
            waitingFor(Wait.forHealthcheck())
        }

    private fun getBaseUrl() = URLBuilder(
        protocol = URLProtocol.HTTP,
        host = synapseDocker.host,
        port = synapseDocker.firstMappedPort
    ).build()

    @Test
    fun shouldLogoutOnDeviceDeletion(): Unit = runBlocking {
        val startedClient1 = registerAndStartClient("client1", "user1", getBaseUrl())
        val startedClient2 = startClient("client2", "user1", getBaseUrl())

        withClue("check client2 is logged in and sync is running") {
            withTimeout(30_000) {
                startedClient2.client.syncState.first { it == SyncState.RUNNING }
                startedClient2.client.loginState.first { it == LOGGED_IN }
            }
        }

        val deleteStep = startedClient1.client.api.devices.deleteDevice("client2").getOrThrow()
        deleteStep.shouldBeInstanceOf<UIA.Step<Unit>>()
            .authenticate(Password(User("user1"), startedClient1.password)).getOrThrow()
            .shouldBeInstanceOf<UIA.Success<Unit>>()

        withClue("check client2 is logged out and sync is stopped") {
            withTimeout(30_000) {
                startedClient2.client.syncState.first { it == SyncState.STOPPED }
                startedClient2.client.loginState.first { it == MatrixClient.LoginState.LOGGED_OUT }
            }
        }

        startedClient1.scope.cancel()
        startedClient2.scope.cancel()
    }
}