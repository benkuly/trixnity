package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class KeySharingIT {

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
        startedClient2 =
            registerAndStartClient("client2", "user2", baseUrl, createExposedRepositoriesModule(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
    }

    @Test
    fun testKeySharing(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            startedClient1.client.verification.getSelfVerificationMethods()
                .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                .firstWithTimeout()

            val bootstrap = startedClient1.client.key.bootstrapCrossSigning()
            withClue("bootstrap client1") {
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            val roomId = withClue("user1 invites user2, so user2 gets user1s keys") {
                startedClient1.client.api.room.createRoom(
                    invite = setOf(startedClient2.client.userId),
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
            }

            withClue("join and wait for join") {
                startedClient1.client.room.getById(roomId).firstWithTimeout { it != null && it.membership == JOIN }
                startedClient2.client.api.room.joinRoom(roomId).getOrThrow()
                startedClient2.client.room.getById(roomId).firstWithTimeout { it != null && it.membership == JOIN }
                // we need to wait until the clients know the room is encrypted
                startedClient1.client.room.getState<EncryptionEventContent>(roomId)
                    .firstWithTimeout { it != null }
                startedClient2.client.room.getState<EncryptionEventContent>(roomId)
                    .firstWithTimeout { it != null }
            }
            withClue("send some messages") {
                startedClient1.client.room.sendMessage(roomId) { text("hi from client1") }
                delay(1_000)
                startedClient2.client.room.sendMessage(roomId) { text("hi from client2") }
            }
            withClue("login with another client and look if keybackup works") {
                val database = newDatabase()
                val repositoriesModule = createExposedRepositoriesModule(database)

                val client3 = MatrixClient.login(
                    baseUrl = URLBuilder(
                        protocol = URLProtocol.HTTP,
                        host = synapseDocker.host,
                        port = synapseDocker.firstMappedPort
                    ).build(),
                    identifier = IdentifierType.User("user1"),
                    password = "user$1passw0rd",
                    repositoriesModule = repositoriesModule,
                    mediaStoreModule = createInMemoryMediaStoreModule(),
                    deviceId = "client3"
                ) { name = "client3" }.getOrThrow()
                client3.startSync()
                client3.syncState.firstWithTimeout { it == SyncState.RUNNING }

                withClue("verify client3") {
                    startedClient1.client.verification.createDeviceVerificationRequest(
                        client3.userId,
                        setOf(client3.deviceId)
                    ).getOrThrow()

                    val client1Verification =
                        startedClient1.client.verification.activeDeviceVerification.firstWithTimeout { it != null }
                    val client3Verification =
                        client3.verification.activeDeviceVerification.firstWithTimeout { it != null }

                    client1Verification.shouldNotBeNull()
                    client3Verification.shouldNotBeNull()

                    client3Verification.state.firstWithTimeout { it is ActiveVerificationState.TheirRequest }
                        .shouldBeInstanceOf<ActiveVerificationState.TheirRequest>().ready()
                    client1Verification.state.firstWithTimeout { it is ActiveVerificationState.Ready }
                        .shouldBeInstanceOf<ActiveVerificationState.Ready>().start(VerificationMethod.Sas)

                    val client1SasVerification =
                        client1Verification.state.firstWithTimeout { it is ActiveVerificationState.Start }
                            .shouldBeInstanceOf<ActiveVerificationState.Start>()
                            .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    val client3SasVerification =
                        client3Verification.state.firstWithTimeout { it is ActiveVerificationState.Start }
                            .shouldBeInstanceOf<ActiveVerificationState.Start>()
                            .method.shouldBeInstanceOf<ActiveSasVerificationMethod>()

                    client3SasVerification.state.firstWithTimeout { it is ActiveSasVerificationState.TheirSasStart }
                        .shouldBeInstanceOf<ActiveSasVerificationState.TheirSasStart>().accept()

                    val client1Comparison =
                        client1SasVerification.state.firstWithTimeout { it is ActiveSasVerificationState.ComparisonByUser }
                            .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()

                    val client3Comparison =
                        client3SasVerification.state.firstWithTimeout { it is ActiveSasVerificationState.ComparisonByUser }
                            .shouldBeInstanceOf<ActiveSasVerificationState.ComparisonByUser>()

                    client1Comparison.decimal shouldBe client3Comparison.decimal
                    client1Comparison.emojis shouldBe client3Comparison.emojis

                    client1Comparison.match()
                    client1SasVerification.state.firstWithTimeout { it is ActiveSasVerificationState.WaitForMacs }
                        .shouldBeInstanceOf<ActiveSasVerificationState.WaitForMacs>()
                    client3Comparison.match()

                    client1Verification.state.firstWithTimeout { it is ActiveVerificationState.Done }
                        .shouldBeInstanceOf<ActiveVerificationState.Done>()
                    client3Verification.state.firstWithTimeout { it is ActiveVerificationState.Done }
                        .shouldBeInstanceOf<ActiveVerificationState.Done>()

                    startedClient1.client.key.getTrustLevel(client3.userId, client3.deviceId)
                        .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                    client3.key.getTrustLevel(startedClient1.client.userId, startedClient1.client.deviceId)
                        .firstWithTimeout { it == DeviceTrustLevel.CrossSigned(true) }
                }

                val events = client3.room.getLastTimelineEvents(roomId)
                    .toFlowList(MutableStateFlow(2))
                    .map { it.map { it.firstWithTimeout().eventId } }
                    .firstWithTimeout { it.size == 2 }
                events[0].shouldNotBeNull().let { client3.room.getTimelineEvent(roomId, it) }
                    .firstWithTimeout { it?.content != null }?.content?.getOrThrow()
                    .shouldBe(RoomMessageEventContent.TextBased.Text("hi from client2", mentions = Mentions()))
                events[1].shouldNotBeNull().let { client3.room.getTimelineEvent(roomId, it) }
                    .firstWithTimeout { it?.content != null }?.content?.getOrThrow()
                    .shouldBe(RoomMessageEventContent.TextBased.Text("hi from client1", mentions = Mentions()))

                client3.closeSuspending()
            }
        }
    }
}