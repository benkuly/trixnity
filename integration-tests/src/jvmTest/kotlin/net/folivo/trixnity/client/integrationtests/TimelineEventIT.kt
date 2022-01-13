package net.folivo.trixnity.client.integrationtests

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
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
class TimelineEventIT {

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
    fun shouldStartEncryptedRoomAndSendMessages(): Unit = runBlocking {
        withTimeout(60_000) {
            val room = client1.api.rooms.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(Event.InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.rooms.joinRoom(room).getOrThrow()

            client1.room.getById(room).first { it?.encryptionAlgorithm == EncryptionAlgorithm.Megolm }
            client2.room.getById(room).first { it?.encryptionAlgorithm == EncryptionAlgorithm.Megolm }

            client1.room.sendMessage(room) { text("Hello!") }
            client2.room.sendMessage(room) { text("Hello to you, too!") }
            client1.room.sendMessage(room) { text("How are you?") }

            val decryptedMessages = mutableSetOf<EventId>()

            client2.room.getLastTimelineEvent(room, scope2)
                .filterNotNull()
                .takeWhile { decryptedMessages.size < 3 }
                .collectLatest { lastTimelineEvent ->
                    var currentTimelineEvent = lastTimelineEvent
                    while (currentCoroutineContext().isActive && decryptedMessages.size < 3) {
                        val currentTimelineEventValue = currentTimelineEvent
                            .filterNotNull()
                            .filter { it.event.content !is EncryptedEventContent || it.decryptedEvent?.isSuccess == true }
                            .first()

                        if (currentTimelineEventValue.event.content is EncryptedEventContent) {
                            decryptedMessages.add(currentTimelineEventValue.eventId)
                        }

                        currentTimelineEvent = currentTimelineEvent
                            .filterNotNull()
                            .map { client2.room.getPreviousTimelineEvent(it, scope2) }
                            .filterNotNull()
                            .first()
                    }
                    // we write a message to escape the collect latest
                    client2.room.sendMessage(room) { text("Fine!") }
                }
        }
    }
}