package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import org.jetbrains.exposed.sql.Database
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class SasVerificationIT {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var scope1: CoroutineScope
    private lateinit var scope2: CoroutineScope
    private lateinit var database1: Database
    private lateinit var database2: Database

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

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        scope1 = CoroutineScope(Dispatchers.Default) + CoroutineName("client1")
        scope2 = CoroutineScope(Dispatchers.Default) + CoroutineName("client2")
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database1 = newDatabase()
        database2 = newDatabase()
        val storeFactory1 = ExposedStoreFactory(database1, Dispatchers.IO, scope1)
        val storeFactory2 = ExposedStoreFactory(database2, Dispatchers.IO, scope2)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            storeFactory = storeFactory1,
            scope = scope1,
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            storeFactory = storeFactory2,
            scope = scope2,
            getLoginInfo = { it.register("user2", password) }
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
    }

    @AfterTest
    fun afterEach() {
        scope1.cancel()
        scope2.cancel()
    }

    @Test
    fun shouldDoSasDeviceVerification(): Unit = runBlocking {
        withTimeout(30_000) {
            client1.verification.createDeviceVerificationRequest(client2.userId, client2.deviceId)
            val client1Verification = client1.verification.activeDeviceVerification.first { it != null }
            val client2Verification = client2.verification.activeDeviceVerification.first { it != null }

            client1Verification.shouldNotBeNull()
            client2Verification.shouldNotBeNull()

            client2Verification.state.first { it is ActiveVerificationState.TheirRequest }
                .shouldBeInstanceOf<ActiveVerificationState.TheirRequest>().ready()

            client1Verification.state.first { it is ActiveVerificationState.Ready }
                .shouldBeInstanceOf<ActiveVerificationState.Ready>().start(VerificationMethod.Sas)

            val client1SasVerification = client1Verification.state.first { it is ActiveVerificationState.Start }
                .shouldBeInstanceOf<ActiveVerificationState.Start>()
                .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
            val client2SasVerification = client2Verification.state.first { it is ActiveVerificationState.Start }
                .shouldBeInstanceOf<ActiveVerificationState.Start>()
                .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()

            client2SasVerification.state.first { it is ActiveSasVerificationState.TheirSasStart }
                .shouldBeInstanceOf<ActiveSasVerificationState.TheirSasStart>().accept()

            client1SasVerification.state.first { it is ActiveSasVerificationState.ComparisonByUser }
                .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>().match()
            client1SasVerification.state.first { it is ActiveSasVerificationState.WaitForMacs }
                .shouldBeInstanceOf<ActiveSasVerificationState.WaitForMacs>()
            client2SasVerification.state.first { it is ActiveSasVerificationState.ComparisonByUser }
                .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>().match()

            client1Verification.state.first { it is ActiveVerificationState.Done }
                .shouldBeInstanceOf<ActiveVerificationState.Done>()
            client2Verification.state.first { it is ActiveVerificationState.Done }
                .shouldBeInstanceOf<ActiveVerificationState.Done>()
        }

        client1.key.getTrustLevel(client2.userId, client2.deviceId, scope1).value shouldBe DeviceTrustLevel.Verified
        client2.key.getTrustLevel(client1.userId, client1.deviceId, scope2).value shouldBe DeviceTrustLevel.Verified
    }
}