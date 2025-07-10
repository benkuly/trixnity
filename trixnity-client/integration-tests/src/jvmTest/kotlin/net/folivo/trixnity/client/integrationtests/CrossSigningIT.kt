package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user.getAccountData
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import net.folivo.trixnity.client.verification.SelfVerificationMethod.CrossSignedDeviceVerification
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.key.DeviceTrustLevel.NotCrossSigned
import net.folivo.trixnity.crypto.key.UserTrustLevel.CrossSigned
import net.folivo.trixnity.crypto.key.UserTrustLevel.NotAllDevicesCrossSigned
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class CrossSigningIT {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var client3: MatrixClient
    private lateinit var database1: Database
    private lateinit var database2: Database
    private lateinit var database3: Database
    private val password = "user$1passw0rd"

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
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
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user1", password) }
        ) {
            name = "client1"
        }.getOrThrow()
        client2 = MatrixClient.login(
            baseUrl = baseUrl,
            identifier = IdentifierType.User("user1"),
            password = password,
            repositoriesModule = repositoriesModule2,
            mediaStoreModule = createInMemoryMediaStoreModule(),
        ) {
            name = "client2"
        }.getOrThrow()
        client3 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule3,
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user3", password) }
        ) {
            name = "client3"
        }.getOrThrow()
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
    fun testCrossSigning(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("wait for client1 self verification to be NoCrossSigningEnabled") {
                client1.verification.getSelfVerificationMethods()
                    .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                    .firstWithTimeout()
            }

            val bootstrap = withClue("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            withClue("user1 invites user3, so user3 gets user1s keys") {
                val roomId = client1.api.room.createRoom(
                    invite = setOf(client3.userId),
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
                client1.room.getById(roomId).firstWithTimeout { it != null }
                client3.api.room.joinRoom(roomId).getOrThrow()
                client3.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
            }

            withClue("wait for client1 self verification to be AlreadyCrossSigned") {
                client1.verification.getSelfVerificationMethods()
                    .filterIsInstance<SelfVerificationMethods.AlreadyCrossSigned>()
                    .firstWithTimeout()
            }

            withClue("bootstrap client3") {
                client3.key.bootstrapCrossSigning().result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }

            withClue("observe trust level with client1 before self verification") {
                client1.key.apply {
                    getTrustLevel(client1.userId).firstWithTimeout { it == NotAllDevicesCrossSigned(true) }
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout { it == NotCrossSigned }
                    getTrustLevel(client3.userId).firstWithTimeout { it == CrossSigned(false) }
                    getTrustLevel(
                        client3.userId,
                        client3.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                }
            }
            withClue("observe trust level with client2 before self verification") {
                client2.key.apply {
                    getTrustLevel(client1.userId).firstWithTimeout { it == NotAllDevicesCrossSigned(false) }
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                    getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout { it == NotCrossSigned }
                    getTrustLevel(client3.userId).firstWithTimeout { it == CrossSigned(false) }
                    getTrustLevel(
                        client3.userId,
                        client3.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                }
            }
            withClue("observe trust level with client3 before self verification") {
                client3.key.apply {
                    getTrustLevel(client1.userId).firstWithTimeout { it == NotAllDevicesCrossSigned(false) }
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                    getTrustLevel(client2.userId, client2.deviceId).firstWithTimeout { it == NotCrossSigned }
                    getTrustLevel(client3.userId).firstWithTimeout { it == CrossSigned(true) }
                    getTrustLevel(
                        client3.userId,
                        client3.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                }
            }

            withClue("self verification of client2") {
                val client2VerificationMethods =
                    client2.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .firstWithTimeout { it.methods.size == 2 }.methods
                client2VerificationMethods.filterIsInstance<CrossSignedDeviceVerification>().size shouldBe 1
                client2VerificationMethods.filterIsInstance<AesHmacSha2RecoveryKey>().size shouldBe 1
                client2VerificationMethods.filterIsInstance<AesHmacSha2RecoveryKey>().first()
                    .verify(bootstrap.recoveryKey).getOrThrow()
                client2.verification.getSelfVerificationMethods()
                    .firstWithTimeout { it == SelfVerificationMethods.AlreadyCrossSigned }
            }

            withClue("observe trust level with client1 after self verification") {
                client1.key.apply {
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    getTrustLevel(
                        client2.userId,
                        client2.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    getTrustLevel(client1.userId).firstWithTimeout { it == CrossSigned(true) }
                }
            }
            withClue("observe trust level with client2 after self verification") {
                client2.key.apply {
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    getTrustLevel(
                        client2.userId,
                        client2.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    getTrustLevel(client1.userId).firstWithTimeout { it == CrossSigned(true) }
                }
            }
            withClue("observe trust level with client3 after self verification") {
                client3.key.apply {
                    getTrustLevel(
                        client1.userId,
                        client1.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                    getTrustLevel(
                        client2.userId,
                        client2.deviceId
                    ).firstWithTimeout { it == DeviceTrustLevel.CrossSigned(false) }
                    getTrustLevel(client1.userId).firstWithTimeout { it == CrossSigned(false) }
                }
            }
            withClue("verification between user1 and user3") {
                client2.verification.createDeviceVerificationRequest(client3.userId, setOf(client3.deviceId))
                val client2Verification = client2.verification.activeDeviceVerification.firstWithTimeout { it != null }
                val client3Verification = client3.verification.activeDeviceVerification.firstWithTimeout { it != null }

                client2Verification.shouldNotBeNull()
                client3Verification.shouldNotBeNull()

                val user1Comparison = async {
                    client2Verification.state.firstWithTimeout { it is ActiveVerificationState.Ready }
                        .shouldBeInstanceOf<ActiveVerificationState.Ready>().start(VerificationMethod.Sas)
                    client2Verification.state.firstWithTimeout { it is ActiveVerificationState.Start }
                        .shouldBeInstanceOf<ActiveVerificationState.Start>()
                        .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                        .state.firstWithTimeout { it is ActiveSasVerificationState.ComparisonByUser }
                        .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()
                }

                val user3Comparison = async {
                    client3Verification.state.firstWithTimeout { it is ActiveVerificationState.TheirRequest }
                        .shouldBeInstanceOf<ActiveVerificationState.TheirRequest>()
                        .ready()
                    val sas = client3Verification.state.firstWithTimeout { it is ActiveVerificationState.Start }
                        .shouldBeInstanceOf<ActiveVerificationState.Start>()
                        .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    sas.state.firstWithTimeout { it is ActiveSasVerificationState.TheirSasStart }
                        .shouldBeInstanceOf<ActiveSasVerificationState.TheirSasStart>()
                        .accept()
                    sas.state.firstWithTimeout { it is ActiveSasVerificationState.ComparisonByUser }
                        .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()
                }

                user1Comparison.await().decimal shouldBe user3Comparison.await().decimal
                user1Comparison.await().emojis shouldBe user3Comparison.await().emojis

                user1Comparison.await().match()
                user3Comparison.await().match()

                client2Verification.state.firstWithTimeout { it is ActiveVerificationState.Done }
                client3Verification.state.firstWithTimeout { it is ActiveVerificationState.Done }
            }

            suspend fun KeyService.checkEverythingVerified() {
                this.getTrustLevel(client1.userId).firstWithTimeout { it == CrossSigned(true) }
                this.getTrustLevel(client1.userId, client1.deviceId)
                    .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                this.getTrustLevel(client2.userId, client2.deviceId)
                    .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                this.getTrustLevel(client3.userId).firstWithTimeout { it == CrossSigned(true) }
                this.getTrustLevel(client3.userId, client3.deviceId)
                    .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
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

    @Test
    fun shouldAllowResetCrossSigning(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("wait for client1 self verification to be NoCrossSigningEnabled") {
                client1.verification.getSelfVerificationMethods()
                    .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                    .firstWithTimeout()
            }

            withClue("bootstrap client1") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }
            val defaultSecret1 = withClue("get account data DefaultSecretKeyEventContent") {
                client2.user.getAccountData<DefaultSecretKeyEventContent>().filterNotNull().firstWithTimeout()
            }

            val bootstrap2 = withClue("reset cross signing by bootstrap client1 again") {
                client1.key.bootstrapCrossSigning().also {
                    it.result.getOrThrow()
                        .shouldBeInstanceOf<UIA.Step<Unit>>()
                        .authenticate(AuthenticationRequest.Password(IdentifierType.User("user1"), password))
                        .getOrThrow()
                        .shouldBeInstanceOf<UIA.Success<Unit>>()
                }
            }

            withClue("get account data DefaultSecretKeyEventContent and check it's not the same") {
                client2.user.getAccountData<DefaultSecretKeyEventContent>().firstWithTimeout { it != defaultSecret1 }
            }
            withClue("self verification of client2") {
                val client2VerificationMethods =
                    client2.verification.getSelfVerificationMethods()
                        .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                        .firstWithTimeout { it.methods.size == 2 }.methods
                client2VerificationMethods.filterIsInstance<CrossSignedDeviceVerification>().size shouldBe 1
                client2VerificationMethods.filterIsInstance<AesHmacSha2RecoveryKey>().size shouldBe 1
                client2VerificationMethods.filterIsInstance<AesHmacSha2RecoveryKey>().first()
                    .verify(bootstrap2.recoveryKey).getOrThrow()

                withClue("wait for client2 self verification to be AlreadyCrossSigned") {
                    client2.verification.getSelfVerificationMethods()
                        .firstWithTimeout { it == SelfVerificationMethods.AlreadyCrossSigned }
                }
            }
        }
    }
}