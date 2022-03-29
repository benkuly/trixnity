package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.core.model.events.Event.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
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
class PushIT {
    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    private val scope = CoroutineScope(Dispatchers.Default)

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
        scope.cancel()
    }

    @Test
    fun testPushNotificationForNormalMessage(): Unit = runBlocking {
        withTimeout(30_000) {
            val room = startedClient1.client.api.rooms.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            startedClient2.client.room.getById(room).first { it?.membership == INVITE }
            startedClient2.client.api.rooms.joinRoom(room).getOrThrow()

            startedClient1.client.room.getById(room).first { it?.encryptionAlgorithm == EncryptionAlgorithm.Megolm }
            startedClient2.client.room.getById(room).first { it?.encryptionAlgorithm == EncryptionAlgorithm.Megolm }

            val lastNotification = startedClient2.client.push.notifications.stateIn(scope, SharingStarted.Eagerly, null)

            startedClient1.client.room.sendMessage(room) { text("Hello ${startedClient2.client.userId.full}!") }
            val lastNotificationContent = lastNotification.filterNotNull().first().content
            lastNotificationContent.shouldBeInstanceOf<TextMessageEventContent>()
            lastNotificationContent.body shouldStartWith "Hello"
        }
    }
}