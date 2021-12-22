package net.folivo.trixnity.client.integrationtests

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import org.jetbrains.exposed.sql.Database
import org.kodein.log.LoggerFactory
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
    private lateinit var scope: CoroutineScope
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun beforeEach() = runBlocking {
        scope = CoroutineScope(Dispatchers.Default)
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database1 = Database.connect("jdbc:h2:mem:timeline-event-test1;DB_CLOSE_DELAY=-1;")
        database2 = Database.connect("jdbc:h2:mem:timeline-event-test2;DB_CLOSE_DELAY=-1;")

        val storeFactory1 = ExposedStoreFactory(database1, Dispatchers.IO, scope, LoggerFactory.default)
        val storeFactory2 = ExposedStoreFactory(database2, Dispatchers.IO, scope, LoggerFactory.default)
        val secureStore = object : SecureStore {
            override val olmPickleKey = ""
        }

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
//            baseHttpClient = HttpClient(Java) { install(Logging) { level = LogLevel.INFO } },
            storeFactory = storeFactory1,
            secureStore = secureStore,
            scope = scope,
            loggerFactory = loggerFactory("user1 🔴"),
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
//            baseHttpClient = HttpClient(Java) { install(Logging) { level = LogLevel.INFO } },
            storeFactory = storeFactory2,
            secureStore = secureStore,
            scope = scope,
            loggerFactory = loggerFactory("user2 🔵"),
            getLoginInfo = { it.register("user2", password) }
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
    }

    @AfterTest
    fun afterEach() {
        scope.cancel()
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

            client2.room.getLastTimelineEvent(room, scope)
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
                            .map { client2.room.getPreviousTimelineEvent(it, scope) }
                            .filterNotNull()
                            .first()
                    }
                    // we write a message to escape the collect latest
                    client2.room.sendMessage(room) { text("Fine!") }
                }
        }
    }
}