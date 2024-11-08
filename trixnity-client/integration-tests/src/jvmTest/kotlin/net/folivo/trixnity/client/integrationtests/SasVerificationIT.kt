package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.client.verification.ActiveVerification
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
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
    private lateinit var client3: MatrixClient
    private lateinit var database1: Database
    private lateinit var database2: Database
    private lateinit var database3: Database

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database1 = newDatabase()
        database2 = newDatabase()
        database3 = newDatabase()
        val repositoriesModule1 = createExposedRepositoriesModule(database1)
        val repositoriesModule2 = createExposedRepositoriesModule(database2)
        val repositoriesModule3 = createExposedRepositoriesModule(database3)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule1,
            mediaStore = InMemoryMediaStore(),
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule2,
            mediaStore = InMemoryMediaStore(),
            getLoginInfo = { it.register("user2", password) }
        ).getOrThrow()
        client3 = MatrixClient.login(
            baseUrl = baseUrl,
            identifier = IdentifierType.User("user1"),
            password = password,
            repositoriesModule = repositoriesModule3,
            mediaStore = InMemoryMediaStore(),
        ) {
            name = "client3"
        }.getOrThrow()
        client1.startSync()
        client2.startSync()
        client3.startSync()
        client1.syncState.first { it == SyncState.RUNNING }
        client2.syncState.first { it == SyncState.RUNNING }
        client3.syncState.first { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client1.close()
        client2.close()
        client3.close()
    }

    @Test
    fun shouldDoSasDeviceVerificationSameUser1(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("bootstrap client3") {
                client3.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client3.userId, setOf(client3.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.first { it != null }
            val client3Verification = client3.verification.activeDeviceVerification.first { it != null }

            checkSasVerification(client1, client3, client1Verification, client3Verification)
        }
    }

    @Test
    fun shouldDoSasDeviceVerificationSameUser2(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client3.userId, setOf(client3.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.first { it != null }
            val client3Verification = client3.verification.activeDeviceVerification.first { it != null }

            checkSasVerification(client1, client3, client1Verification, client3Verification)
        }
    }

    @Test
    fun shouldDoSasDeviceVerification(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withClue("bootstrap client2") {
                client2.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client2.userId, setOf(client2.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.first { it != null }
            val client2Verification = client2.verification.activeDeviceVerification.first { it != null }

            checkSasVerification(client1, client2, client1Verification, client2Verification)
        }
    }

    @Test
    fun shouldDoSasUserVerification(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withClue("bootstrap client2") {
                client2.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            val client1Verification = client1.verification.createUserVerificationRequest(client2.userId).getOrThrow()

            client2.api.room.joinRoom(client1Verification.roomId)
            val client2Verification = client2.verification.getActiveUserVerification(
                client1Verification.roomId,
                client1Verification.requestEventId
            )

            checkSasVerification(client1, client2, client1Verification, client2Verification)
        }
    }

    private suspend fun checkSasVerification(
        client1: MatrixClient,
        client2: MatrixClient,
        client1Verification: ActiveVerification?,
        client2Verification: ActiveVerification?,
    ) {
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
            .first { it == DeviceTrustLevel.CrossSigned(true) }
        client2.key.getTrustLevel(client1.userId, client1.deviceId)
            .first { it == DeviceTrustLevel.CrossSigned(true) }
    }
}