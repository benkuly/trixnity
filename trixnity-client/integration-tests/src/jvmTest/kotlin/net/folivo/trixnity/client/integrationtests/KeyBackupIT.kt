package net.folivo.trixnity.client.integrationtests

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
import net.folivo.trixnity.client.cryptodriver.vodozemac.vodozemac
import net.folivo.trixnity.client.media.inMemory
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.repository.exposed.exposed
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.classicLogin
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class KeyBackupIT {

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
                    baseUrl = baseUrl,
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