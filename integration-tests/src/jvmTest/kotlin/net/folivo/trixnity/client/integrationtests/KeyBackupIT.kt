package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.client.verification.IVerificationService.SelfVerificationMethods
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest.Password
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
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
class KeyBackupIT {

    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

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
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 = registerAndStartClient("client1", "user1", baseUrl)
        startedClient2 = registerAndStartClient("client2", "user2", baseUrl)
    }

    @AfterTest
    fun afterEach() {
        startedClient1.scope.cancel()
        startedClient2.scope.cancel()
    }

    @Test
    fun testCrossSigning(): Unit = runBlocking {
        withTimeout(30_000) {
            startedClient1.client.verification.getSelfVerificationMethods(startedClient1.scope)
                .filterIsInstance<SelfVerificationMethods.NoCrossSigningEnabled>()
                .first()

            val bootstrap = startedClient1.client.key.bootstrapCrossSigning()
            withClue("bootstrap client1") {
                bootstrap.result.getOrThrow()
                    .shouldBeInstanceOf<UIA.Step<Unit>>()
                    .authenticate(Password(IdentifierType.User("user1"), startedClient1.password)).getOrThrow()
                    .shouldBeInstanceOf<UIA.Success<Unit>>()
            }
            val roomId = withClue("user1 invites user2, so user2 gets user1s keys") {
                startedClient1.client.api.rooms.createRoom(
                    invite = setOf(startedClient2.client.userId),
                    initialState = listOf(Event.InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
            }

            withClue("join and wait for join") {
                startedClient1.client.room.getById(roomId).first { it != null && it.membership == JOIN }
                startedClient2.client.api.rooms.joinRoom(roomId).getOrThrow()
                startedClient2.client.room.getById(roomId).first { it != null && it.membership == JOIN }
                // we need to wait until the clients know the room is encrypted
                startedClient1.client.room.getState<EncryptionEventContent>(roomId, scope = startedClient1.scope)
                    .first { it != null }
                startedClient2.client.room.getState<EncryptionEventContent>(roomId, scope = startedClient2.scope)
                    .first { it != null }
            }
            withClue("send some messages") {
                startedClient1.client.room.sendMessage(roomId) { text("hi from client1") }
                delay(1_000)
                startedClient2.client.room.sendMessage(roomId) { text("hi from client2") }
            }
            withClue("login with another client and look if keybackup works") {
                val scope = CoroutineScope(Dispatchers.Default) + CoroutineName("client3")
                val database = newDatabase()
                val storeFactory = ExposedStoreFactory(database, Dispatchers.IO, scope)

                val client3 = MatrixClient.login(
                    baseUrl = URLBuilder(
                        protocol = URLProtocol.HTTP,
                        host = synapseDocker.host,
                        port = synapseDocker.firstMappedPort
                    ).build(),
                    identifier = IdentifierType.User("user1"),
                    passwordOrToken = "user$1passw0rd",
                    storeFactory = storeFactory,
                    scope = scope,
                ).getOrThrow()
                client3.startSync()
                client3.syncState.first { it == SyncState.RUNNING }

                withClue("self verify client3") {
                    val client3VerificationMethods =
                        client3.verification.getSelfVerificationMethods(scope)
                            .filterIsInstance<SelfVerificationMethods.CrossSigningEnabled>()
                            .first().methods
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.CrossSignedDeviceVerification>().size shouldBe 1
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>().size shouldBe 1
                    client3VerificationMethods.filterIsInstance<SelfVerificationMethod.AesHmacSha2RecoveryKey>()
                        .first()
                        .verify(bootstrap.recoveryKey).getOrThrow()
                }

                val events = client3.room.getLastTimelineEvents(roomId)
                    .toFlowList(MutableStateFlow(2))
                    .map { it.map { it.value?.eventId } }
                    .first { it.size == 2 }
                events[0]!!.let { client3.room.getTimelineEvent(it, roomId, scope) }
                    .first { it?.content != null }?.content?.getOrThrow()
                    .shouldBe(RoomMessageEventContent.TextMessageEventContent("hi from client2"))
                events[1]!!.let { client3.room.getTimelineEvent(it, roomId, scope) }
                    .first { it?.content != null }?.content?.getOrThrow()
                    .shouldBe(RoomMessageEventContent.TextMessageEventContent("hi from client1"))
                scope.cancel()
            }
        }
    }
}