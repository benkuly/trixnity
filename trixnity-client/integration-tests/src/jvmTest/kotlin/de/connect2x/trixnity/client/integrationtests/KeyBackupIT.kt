package de.connect2x.trixnity.client.integrationtests

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
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.room.toFlowList
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.verification.SelfVerificationMethod
import de.connect2x.trixnity.client.verification.VerificationService.SelfVerificationMethods
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class KeyBackupIT : TrixnityBaseTest() {

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
            registerAndStartClient("client1", "user1", baseUrl, RepositoriesModule.exposed(newDatabase()))
        startedClient2 =
            registerAndStartClient("client2", "user2", baseUrl, RepositoriesModule.exposed(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
    }

    @Test
    fun testKeyBackup(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            startedClient1.client.verification.getSelfVerificationMethods()
                .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                .firstWithTimeout()

            val bootstrap = startedClient1.client.key.bootstrapCrossSigning()
            withCluePrintln("bootstrap client1") {
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            val roomId = withCluePrintln("user1 invites user2, so user2 gets user1s keys") {
                startedClient1.client.api.room.createRoom(
                    invite = setOf(startedClient2.client.userId),
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
            }

            withCluePrintln("join and wait for join") {
                startedClient1.client.room.getById(roomId).firstWithTimeout { it != null && it.membership == JOIN }
                startedClient2.client.api.room.joinRoom(roomId).getOrThrow()
                startedClient2.client.room.getById(roomId).firstWithTimeout { it != null && it.membership == JOIN }
                // we need to wait until the clients know the room is encrypted
                startedClient1.client.room.getState<EncryptionEventContent>(roomId)
                    .firstWithTimeout { it != null }
                startedClient2.client.room.getState<EncryptionEventContent>(roomId)
                    .firstWithTimeout { it != null }
            }
            withCluePrintln("send some messages") {
                startedClient1.client.room.sendMessage(roomId) { text("hi from client1") }
                delay(1_000)
                startedClient2.client.room.sendMessage(roomId) { text("hi from client2") }
            }
            withCluePrintln("login with another client and look if keybackup works") {
                val database = newDatabase()
                val repositoriesModule = RepositoriesModule.exposed(database)

                val baseUrl = URLBuilder(
                    protocol = URLProtocol.HTTP,
                    host = synapseDocker.host,
                    port = synapseDocker.firstMappedPort
                ).build()
                val client3 = MatrixClient.create(
                    repositoriesModule = repositoriesModule,
                    mediaStoreModule = MediaStoreModule.inMemory(),
                    cryptoDriverModule = CryptoDriverModule.vodozemac(),
                    authProviderData = MatrixClientAuthProviderData.classicLogin(
                        baseUrl = baseUrl,
                        identifier = IdentifierType.User("user1"),
                        password = "user$1passw0rd",
                    ).getOrThrow(),
                ).getOrThrow()
                client3.startSync()
                client3.syncState.firstWithTimeout { it == SyncState.RUNNING }

                withCluePrintln("self verify client3") {
                    val client3VerificationMethods =
                        client3.verification.getSelfVerificationMethods()
                            .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                            .firstWithTimeout().methods
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.CrossSignedDeviceVerification>().size shouldBe 1
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>().size shouldBe 1
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                        .first()
                        .verify(bootstrap.recoveryKey).getOrThrow()
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