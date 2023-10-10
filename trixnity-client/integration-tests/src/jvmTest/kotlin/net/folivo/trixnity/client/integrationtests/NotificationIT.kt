package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.notification
import net.folivo.trixnity.client.notification.NotificationService
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class NotificationIT {
    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    private val scope = CoroutineScope(Dispatchers.Default)

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 = registerAndStartClient(
            "client1", "user1", baseUrl,
            createExposedRepositoriesModule(newDatabase())
        )
        startedClient2 =
            registerAndStartClient("client2", "user2", baseUrl, createExposedRepositoriesModule(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        runBlocking {
            startedClient1.client.stop()
            startedClient2.client.stop()
        }
        scope.cancel()
    }

    @Test
    fun testPushNotificationForNormalMessage(): Unit = runBlocking {
        withTimeout(30_000) {
            val notifications = startedClient2.client.notification.getNotifications()
                .scan(listOf<NotificationService.Notification>()) { old, new -> old + new }
                .stateIn(scope)

            val room = startedClient1.client.api.rooms.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()

            withClue("first notification") {
                notifications.first { it.size == 1 }.getOrNull(0).shouldNotBeNull()
                    .event.shouldBeInstanceOf<StrippedStateEvent<*>>()
                    .content.shouldBeInstanceOf<MemberEventContent>().displayName shouldBe "user2"
            }

            startedClient2.client.room.getById(room).first { it?.membership == INVITE }
            startedClient2.client.api.rooms.joinRoom(room).getOrThrow()

            startedClient1.client.room.sendMessage(room) { text("Hello ${startedClient2.client.userId.full}!") }
            withClue("second notification") {
                notifications.first { it.size == 3 }.getOrNull(2).shouldNotBeNull()
                    .event.content.shouldBeInstanceOf<TextMessageEventContent>()
                    .body shouldStartWith "Hello"
            }
        }
    }
}