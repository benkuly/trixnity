package de.connect2x.trixnity.client.integrationtests

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState.*
import de.connect2x.trixnity.client.verification.ActiveVerification
import de.connect2x.trixnity.client.verification.ActiveVerificationState.*
import de.connect2x.trixnity.clientserverapi.client.*
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
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
        val repositoriesModule1 = RepositoriesModule.exposed(database1)
        val repositoriesModule2 = RepositoriesModule.exposed(database2)
        val repositoriesModule3 = RepositoriesModule.exposed(database3)

        client1 = MatrixClient.create(
            repositoriesModule = repositoriesModule1,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user1", password)
            }.getOrThrow(),
            configuration = {
                name = "client1"
            },
        ).getOrThrow()
        client2 = MatrixClient.create(
            repositoriesModule = repositoriesModule2,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user2", password)
            }.getOrThrow(),
            configuration = {
                name = "client2"
            },
        ).getOrThrow()
        client3 = MatrixClient.create(
            repositoriesModule = repositoriesModule3,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLogin(
                baseUrl = baseUrl,
                identifier = IdentifierType.User("user1"),
                password = password,
            ).getOrThrow(),
            configuration = {
                name = "client3"
            },
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
        client3.startSync()
        client1.syncState.firstWithTimeout { it == SyncState.RUNNING }
        client2.syncState.firstWithTimeout { it == SyncState.RUNNING }
        client3.syncState.firstWithTimeout { it == SyncState.RUNNING }
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
            withCluePrintln("bootstrap client3") {
                client3.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client3.userId, setOf(client3.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.firstWithTimeout { it != null }
            val client3Verification = client3.verification.activeDeviceVerification.firstWithTimeout { it != null }

            checkSasVerification(client1, client3, client1Verification, client3Verification)
        }
    }

    @Test
    fun shouldDoSasDeviceVerificationSameUser2(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withCluePrintln("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client3.userId, setOf(client3.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.firstWithTimeout { it != null }
            val client3Verification = client3.verification.activeDeviceVerification.firstWithTimeout { it != null }

            checkSasVerification(client1, client3, client1Verification, client3Verification)
        }
    }

    @Test
    fun shouldDoSasDeviceVerification(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withCluePrintln("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withCluePrintln("bootstrap client2") {
                client2.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client1.verification.createDeviceVerificationRequest(client2.userId, setOf(client2.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.firstWithTimeout { it != null }
            val client2Verification = client2.verification.activeDeviceVerification.firstWithTimeout { it != null }

            checkSasVerification(client1, client2, client1Verification, client2Verification)
        }
    }

    @Test
    fun shouldDoSasDeviceVerification2(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withCluePrintln("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withCluePrintln("bootstrap client2") {
                client2.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            client2.verification.createDeviceVerificationRequest(client1.userId, setOf(client1.deviceId))
            val client1Verification = client1.verification.activeDeviceVerification.firstWithTimeout { it != null }
            val client2Verification = client2.verification.activeDeviceVerification.firstWithTimeout { it != null }

            // change the order
            checkSasVerification(client2, client1, client2Verification, client1Verification)
        }
    }

    @Test
    fun shouldDoSasUserVerification(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withCluePrintln("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withCluePrintln("bootstrap client2") {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun checkSasVerification(
        client1: MatrixClient,
        client2: MatrixClient,
        client1Verification: ActiveVerification?,
        client2Verification: ActiveVerification?,
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        try {
            client1Verification.shouldNotBeNull()
            client2Verification.shouldNotBeNull()

            client2Verification.state.firstWithTimeout { it is TheirRequest }
                .shouldBeInstanceOf<TheirRequest>().ready()

            client1Verification.state.firstWithTimeout { it is Ready }
                .shouldBeInstanceOf<Ready>().start(VerificationMethod.Sas)

            val client2OverridesSasRequest = client2.userId.full < client1.userId.full ||
                    (client2.userId == client1.userId && client2.deviceId < client1.deviceId)
            if (client2OverridesSasRequest) {
                // this should replace the other verification request
                client2Verification.state.firstWithTimeout { it is Ready }
                    .shouldBeInstanceOf<Ready>().start(VerificationMethod.Sas)
            }

            val client1SasVerificationState = client1Verification.state.flatMapLatest { verificationState ->
                if (verificationState is Start) {
                    (verificationState.method as? ActiveSasVerificationMethod)?.state ?: flowOf(null)
                } else flowOf(null)
            }.stateIn(coroutineScope)
            val client2SasVerificationState = client2Verification.state.flatMapLatest { verificationState ->
                if (verificationState is Start) {
                    (verificationState.method as? ActiveSasVerificationMethod)?.state ?: flowOf(null)
                } else flowOf(null)
            }.stateIn(coroutineScope)

            if (client2OverridesSasRequest) {
                client1SasVerificationState.firstWithTimeout { it is TheirSasStart }
                    .shouldBeInstanceOf<TheirSasStart>().accept()
            } else {
                client2SasVerificationState.firstWithTimeout { it is TheirSasStart }
                    .shouldBeInstanceOf<TheirSasStart>().accept()
            }

            val client1Comparison = client1SasVerificationState.firstWithTimeout { it is ComparisonByUser }
                .shouldBeInstanceOf<ComparisonByUser>()

            val client2Comparison =
                client2SasVerificationState.firstWithTimeout { it is ComparisonByUser }
                    .shouldBeInstanceOf<ComparisonByUser>()

            client1Comparison.decimal shouldBe client2Comparison.decimal
            client1Comparison.emojis shouldBe client2Comparison.emojis

            client1Comparison.match()
            client1SasVerificationState.firstWithTimeout { it is WaitForMacs }
                .shouldBeInstanceOf<WaitForMacs>()
            client2Comparison.match()

            client1Verification.state.firstWithTimeout { it is Done }
                .shouldBeInstanceOf<Done>()
            client2Verification.state.firstWithTimeout { it is Done }
                .shouldBeInstanceOf<Done>()

            client1.key.getTrustLevel(client2.userId, client2.deviceId)
                .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
            client2.key.getTrustLevel(client1.userId, client1.deviceId)
                .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
        } finally {
            coroutineScope.cancel()
        }
    }
}