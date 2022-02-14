package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequest
import net.folivo.trixnity.client.key.DeviceTrustLevel.*
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.UserTrustLevel.CrossSigned
import net.folivo.trixnity.client.key.UserTrustLevel.NotAllDevicesCrossSigned
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import net.folivo.trixnity.client.verification.SelfVerificationMethod.CrossSignedDeviceVerification
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
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
class CrossSigningIT {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var client3: MatrixClient
    private lateinit var scope1: CoroutineScope
    private lateinit var scope2: CoroutineScope
    private lateinit var scope3: CoroutineScope
    private lateinit var database1: Database
    private lateinit var database2: Database
    private lateinit var database3: Database
    private val password = "user$1passw0rd"

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
        scope3 = CoroutineScope(Dispatchers.Default) + CoroutineName("client3")
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database1 = newDatabase()
        database2 = newDatabase()
        database3 = newDatabase()
        val storeFactory1 = ExposedStoreFactory(database1, Dispatchers.IO, scope1)
        val storeFactory2 = ExposedStoreFactory(database2, Dispatchers.IO, scope2)
        val storeFactory3 = ExposedStoreFactory(database3, Dispatchers.IO, scope3)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            storeFactory = storeFactory1,
            scope = scope1,
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.login(
            baseUrl = baseUrl,
            identifier = IdentifierType.User("user1"),
            passwordOrToken = password,
            storeFactory = storeFactory2,
            scope = scope2,
        ).getOrThrow()
        client3 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            storeFactory = storeFactory3,
            scope = scope3,
            getLoginInfo = { it.register("user3", password) }
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
        client3.startSync()
        client1.syncState.first { it == SyncApiClient.SyncState.RUNNING }
        client2.syncState.first { it == SyncApiClient.SyncState.RUNNING }
        client3.syncState.first { it == SyncApiClient.SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        scope1.cancel()
        scope2.cancel()
        scope3.cancel()
    }

    @Test
    fun testCrossSigning(): Unit = runBlocking {
        withTimeout(30_000) {
            client1.verification.getSelfVerificationMethods(scope1).first { it?.isEmpty() == true }

            val bootstrap = client1.key.bootstrapCrossSigning()
            withClue("bootstrap client1") {
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.UIAStep<Unit>>()
                    .authenticate(AuthenticationRequest.Password(IdentifierType.User("user1"), password)).getOrThrow()
                    .shouldBeInstanceOf<UIA.UIASuccess<Unit>>()
            }
            withClue("user1 invites user3, so user3 gets user1s keys") {
                val roomId = client1.api.rooms.createRoom(
                    invite = setOf(client3.userId),
                    initialState = listOf(Event.InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
                client1.room.getById(roomId).first { it != null }
                client3.api.rooms.joinRoom(roomId).getOrThrow()
                client3.room.getById(roomId).first { it != null && it.membership == JOIN }
            }

            client1.verification.getSelfVerificationMethods(scope1).first { it?.isEmpty() == true }

            withClue("bootstrap client3") {
                client3.key.bootstrapCrossSigning().result.getOrThrow()
                    .shouldBeInstanceOf<UIA.UIAStep<Unit>>()
                    .authenticate(AuthenticationRequest.Password(IdentifierType.User("user3"), password)).getOrThrow()
                    .shouldBeInstanceOf<UIA.UIASuccess<Unit>>()
            }

            withClue("observe trust level with client1 before self verification") {
                client1.key.apply {
                    getTrustLevel(client1.userId, scope1).first { it == NotAllDevicesCrossSigned(true) }
                    getTrustLevel(client1.userId, client1.deviceId, scope1).first { it == Verified }
                    getTrustLevel(client2.userId, client2.deviceId, scope1).first { it == NotCrossSigned }
                    getTrustLevel(client3.userId, scope1).first { it == CrossSigned(false) }
                    getTrustLevel(client3.userId, client3.deviceId, scope1).first { it == NotVerified }
                }
            }
            withClue("observe trust level with client2 before self verification") {
                client2.key.apply {
                    getTrustLevel(client1.userId, scope2).first { it == NotAllDevicesCrossSigned(false) }
                    getTrustLevel(client1.userId, client1.deviceId, scope2).first { it == NotVerified }
                    getTrustLevel(client2.userId, client2.deviceId, scope2).first { it == NotCrossSigned }
                    getTrustLevel(client3.userId, scope2).first { it == CrossSigned(false) }
                    getTrustLevel(client3.userId, client3.deviceId, scope2).first { it == NotVerified }
                }
            }
            withClue("observe trust level with client3 before self verification") {
                client3.key.apply {
                    getTrustLevel(client1.userId, scope3).first { it == NotAllDevicesCrossSigned(false) }
                    getTrustLevel(client1.userId, client1.deviceId, scope3).first { it == NotVerified }
                    getTrustLevel(client2.userId, client2.deviceId, scope3).first { it == NotCrossSigned }
                    getTrustLevel(client3.userId, scope3).first { it == CrossSigned(true) }
                    getTrustLevel(client3.userId, client3.deviceId, scope3).first { it == Verified }
                }
            }

            withClue("self verification of client2") {
                val client2VerificationMethods =
                    client2.verification.getSelfVerificationMethods(scope2).first { it?.size == 2 }
                client2VerificationMethods?.filterIsInstance<CrossSignedDeviceVerification>()?.size shouldBe 1
                client2VerificationMethods?.filterIsInstance<AesHmacSha2RecoveryKey>()?.size shouldBe 1
                client2VerificationMethods!!.filterIsInstance<AesHmacSha2RecoveryKey>().first()
                    .verify(bootstrap.recoveryKey).getOrThrow()
            }

            withClue("observe trust level with client1 after self verification") {
                client1.key.apply {
                    getTrustLevel(client1.userId, client1.deviceId, scope1).first { it == Verified }
                    getTrustLevel(client2.userId, client2.deviceId, scope1).first { it == Verified }
                    getTrustLevel(client1.userId, scope1).first { it == CrossSigned(true) }
                }
            }
            withClue("observe trust level with client2 after self verification") {
                client2.key.apply {
                    getTrustLevel(client1.userId, client1.deviceId, scope2).first { it == Verified }
                    getTrustLevel(client2.userId, client2.deviceId, scope2).first { it == Verified }
                    getTrustLevel(client1.userId, scope2).first { it == CrossSigned(true) }
                }
            }
            withClue("observe trust level with client3 after self verification") {
                client3.key.apply {
                    getTrustLevel(client1.userId, client1.deviceId, scope3).first { it == NotVerified }
                    getTrustLevel(client2.userId, client2.deviceId, scope3).first { it == NotVerified }
                    getTrustLevel(client1.userId, scope3).first { it == CrossSigned(false) }
                }
            }
            withClue("verification between client2 and client3") {
                client2.verification.createDeviceVerificationRequest(client3.userId, client3.deviceId)
                val client2Verification = client2.verification.activeDeviceVerification.first { it != null }
                val client3Verification = client3.verification.activeDeviceVerification.first { it != null }

                client2Verification.shouldNotBeNull()
                client3Verification.shouldNotBeNull()

                scope2.launch {
                    client2Verification.state.first { it is ActiveVerificationState.Ready }
                        .shouldBeInstanceOf<ActiveVerificationState.Ready>().start(VerificationMethod.Sas)
                    client2Verification.state.first { it is ActiveVerificationState.Start }
                        .shouldBeInstanceOf<ActiveVerificationState.Start>()
                        .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                        .state.first { it is ActiveSasVerificationState.ComparisonByUser }
                        .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()
                        .match()
                }

                scope3.launch {
                    client3Verification.state.first { it is ActiveVerificationState.TheirRequest }
                        .shouldBeInstanceOf<ActiveVerificationState.TheirRequest>()
                        .ready()
                    val sas = client3Verification.state.first { it is ActiveVerificationState.Start }
                        .shouldBeInstanceOf<ActiveVerificationState.Start>()
                        .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    sas.state.first { it is ActiveSasVerificationState.TheirSasStart }
                        .shouldBeInstanceOf<ActiveSasVerificationState.TheirSasStart>()
                        .accept()
                    sas.state.first { it is ActiveSasVerificationState.ComparisonByUser }
                        .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()
                        .match()
                }

                client2Verification.state.first { it is ActiveVerificationState.Done }
                client3Verification.state.first { it is ActiveVerificationState.Done }
            }

            suspend fun KeyService.checkEverythingVerified() {
                this.getTrustLevel(client1.userId, scope1).first { it == CrossSigned(true) }
                this.getTrustLevel(client1.userId, client1.deviceId, scope1).first { it == Verified }
                this.getTrustLevel(client2.userId, client2.deviceId, scope1).first { it == Verified }
                this.getTrustLevel(client3.userId, scope1).first { it == CrossSigned(true) }
                this.getTrustLevel(client3.userId, client3.deviceId, scope1).first { it == Verified }
            }

            withClue("observe trust level with client1 after user verification") {
                client1.key.checkEverythingVerified()
            }
            withClue("observe trust level with client2 after user verification") {
                client2.key.checkEverythingVerified()
            }
            withClue("observe trust level with client3 after user verification") {
                client3.key.checkEverythingVerified()
            }
        }
    }
}