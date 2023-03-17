package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.key
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
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
    val synapseDocker = synapseDocker()

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
        val repositoriesModule1 = createExposedRepositoriesModule(database1)
        val repositoriesModule2 = createExposedRepositoriesModule(database2)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule1,
            mediaStore = InMemoryMediaStore(),
            scope = scope1,
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule2,
            mediaStore = InMemoryMediaStore(),
            scope = scope2,
            getLoginInfo = { it.register("user2", password) }
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
        client1.syncState.first { it == SyncState.RUNNING }
        client2.syncState.first { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        scope1.cancel()
        scope2.cancel()
    }

    @Test
    fun shouldDoSasDeviceVerification(): Unit = runBlocking {
        withTimeout(30_000) {
            client1.verification.createDeviceVerificationRequest(client2.userId, setOf(client2.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.first { it != null }
            val client2Verification = client2.verification.activeDeviceVerification.first { it != null }

            client1Verification.shouldNotBeNull()
            client2Verification.shouldNotBeNull()

            client2Verification.state.first { it is TheirRequest }
                .shouldBeInstanceOf<TheirRequest>().ready()

            client1Verification.state.first { it is Ready }
                .shouldBeInstanceOf<Ready>().start(VerificationMethod.Sas)

            val client1SasVerification = client1Verification.state.first { it is Start }
                .shouldBeInstanceOf<Start>()
                .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
            val client2SasVerification = client2Verification.state.first { it is Start }
                .shouldBeInstanceOf<Start>()
                .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()

            client2SasVerification.state.first { it is TheirSasStart }
                .shouldBeInstanceOf<TheirSasStart>().accept()

            val client1Comparison = client1SasVerification.state.first { it is ComparisonByUser }
                .shouldBeInstanceOf<ComparisonByUser>()

            val client2Comparison = client2SasVerification.state.first { it is ComparisonByUser }
                .shouldBeInstanceOf<ComparisonByUser>()

            client1Comparison.decimal shouldBe client2Comparison.decimal
            client1Comparison.emojis shouldBe client2Comparison.emojis

            client1Comparison.match()
            client1SasVerification.state.first { it is WaitForMacs }
                .shouldBeInstanceOf<WaitForMacs>()
            client2Comparison.match()

            client1Verification.state.first { it is Done }
                .shouldBeInstanceOf<Done>()
            client2Verification.state.first { it is Done }
                .shouldBeInstanceOf<Done>()

            client1.key.getTrustLevel(client2.userId, client2.deviceId)
                .first { it == DeviceTrustLevel.Valid(true) }
            client2.key.getTrustLevel(client1.userId, client1.deviceId)
                .first { it == DeviceTrustLevel.Valid(true) }
        }
    }
}