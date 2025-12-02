package net.folivo.trixnity.client.integrationtests

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.notification
import net.folivo.trixnity.client.notification.Notification
import net.folivo.trixnity.client.notification.NotificationService
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.message.mentions
import net.folivo.trixnity.client.room.message.replace
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user
import net.folivo.trixnity.client.user.getAccountData
import net.folivo.trixnity.clientserverapi.model.push.SetPushRule
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.model.push.ServerDefaultPushRules
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private val log = KotlinLogging.logger("net.folivo.trixnity.client.integrationtests.NotificationIT")

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
        startedClient1.client.close()
        startedClient2.client.close()
        scope.cancel()
    }

    @Test
    fun notifications(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val notifications = startedClient2.client.notification.getAll().flatten().stateIn(scope)

            suspend fun checkNotifications(check: (List<Notification>) -> Boolean): List<Notification> {
                startedClient2.client.syncOnce()
                startedClient2.client.notification.processPending()
                return notifications.firstWithTimeout { check(it) }
            }

            val rooms = withCluePrintln("create rooms") {
                val unencryptedRoom = startedClient1.client.api.room.createRoom().getOrThrow()
                val encryptedRoom = startedClient1.client.api.room.createRoom(
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
                val unencryptedRoomWithoutNotifications = startedClient1.client.api.room.createRoom().getOrThrow()
                val encryptedRoomWithoutNotifications = startedClient1.client.api.room.createRoom(
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
                val currentPushRules = startedClient2.client.user.getAccountData<PushRulesEventContent>().first()
                startedClient2.client.api.push.setPushRule(
                    "global",
                    PushRuleKind.OVERRIDE,
                    unencryptedRoomWithoutNotifications.full,
                    SetPushRule.Request(
                        conditions = setOf(
                            PushCondition.EventMatch(
                                key = "room_id",
                                pattern = unencryptedRoomWithoutNotifications.full
                            )
                        ),
                        actions = setOf(),
                    ),
                )
                startedClient2.client.api.push.setPushRule(
                    "global",
                    PushRuleKind.OVERRIDE,
                    encryptedRoomWithoutNotifications.full,
                    SetPushRule.Request(
                        conditions = setOf(
                            PushCondition.EventMatch(
                                key = "room_id",
                                pattern = encryptedRoomWithoutNotifications.full
                            )
                        ),
                        actions = setOf(),
                    ),
                )
                startedClient2.client.api.push.setPushRuleEnabled(
                    "global",
                    PushRuleKind.UNDERRIDE,
                    ServerDefaultPushRules.RoomOneToOne.id,
                    false,
                )
                startedClient2.client.api.push.setPushRuleEnabled(
                    "global",
                    PushRuleKind.UNDERRIDE,
                    ServerDefaultPushRules.Message.id,
                    false,
                )
                startedClient2.client.user.getAccountData<PushRulesEventContent>()
                    .firstWithTimeout { it != currentPushRules }
                listOf(
                    unencryptedRoom,
                    encryptedRoom,
                    unencryptedRoomWithoutNotifications,
                    encryptedRoomWithoutNotifications
                )
            }
            val roomsWithNotifications = 2

            withCluePrintln("send invites") {
                rooms.forEach { room ->
                    startedClient1.client.api.room.inviteUser(room, startedClient2.client.userId).getOrThrow()
                }
            }

            withCluePrintln("state notifications") {
                checkNotifications { it.size == roomsWithNotifications }.forEach { notification ->
                    val stateEvent = notification.shouldBeInstanceOf<Notification.State>().stateEvent
                    stateEvent.roomId shouldBeIn rooms.take(roomsWithNotifications)
                    stateEvent.content.shouldBeInstanceOf<MemberEventContent>().displayName shouldBe "user2"
                }
            }

            withCluePrintln("join rooms") {
                rooms.forEach { room ->
                    startedClient2.client.room.getById(room).firstWithTimeout { it?.membership == INVITE }
                    startedClient2.client.api.room.joinRoom(room).getOrThrow()
                }
            }

            withCluePrintln("no state notifications") {
                checkNotifications { it.isEmpty() }
            }

            val helloMessage = "Hello!"

            withCluePrintln("no notifications from us") {
                rooms.forEach { room ->
                    startedClient2.client.room.sendMessage(room) { text("Hi!") }
                }
                startedClient2.client.room.waitForOutboxSent()

                checkNotifications { it.isEmpty() }
            }

            withCluePrintln("send message and redact notification") {
                val notificationMessages = sendMessageAndReceiveNotifications(
                    rooms = rooms,
                    roomsWithNotifications = roomsWithNotifications,
                    helloMessage = helloMessage,
                    checkNotifications = ::checkNotifications
                )

                withCluePrintln("send redact") {
                    rooms.forEachIndexed { index, room ->
                        startedClient1.client.api.room.redactEvent(room, notificationMessages[index])
                    }
                }

                withCluePrintln("receive redacted notifications") {
                    checkNotifications { it.isEmpty() }
                }
            }

            withCluePrintln("send message and replace without notification") {
                val notificationMessages = sendMessageAndReceiveNotifications(
                    rooms = rooms,
                    roomsWithNotifications = roomsWithNotifications,
                    helloMessage = helloMessage,
                    checkNotifications = ::checkNotifications
                )

                withCluePrintln("send replace") {
                    rooms.forEachIndexed { index, room ->
                        startedClient1.client.room.sendMessage(room) {
                            text("Hello!")
                            replace(notificationMessages[index])
                            // no mentions
                        }
                    }
                    startedClient1.client.room.waitForOutboxSent()
                }

                withCluePrintln("receive removed notifications") {
                    startedClient2.client.notification.processPending()
                    checkNotifications { it.isEmpty() }
                }
            }

            val notificationMessages = withCluePrintln("send message and replace notification") {
                val notificationMessages = sendMessageAndReceiveNotifications(
                    rooms = rooms,
                    roomsWithNotifications = roomsWithNotifications,
                    helloMessage = helloMessage,
                    checkNotifications = ::checkNotifications
                )

                val replaceMessages = withCluePrintln("send replace") {
                    rooms.mapIndexed { index, room ->
                        startedClient1.client.room.sendMessage(room) {
                            text("$helloMessage!!")
                            replace(notificationMessages[index])
                            mentions(startedClient2.client.userId)
                        }.let { transactionId ->
                            startedClient1.client.room.getOutbox(room).flatten()
                                .mapNotNull { it.find { it.transactionId == transactionId }?.eventId }
                                .firstWithTimeout()
                        }
                    }
                }

                withCluePrintln("receive replaced notifications") {
                    checkNotifications {
                        it.size == roomsWithNotifications && it.any {
                            val content = (it as? Notification.Message)?.timelineEvent?.content?.getOrNull()
                            (content as? RoomMessageEventContent.TextBased.Text)?.body == "$helloMessage!!"
                        }
                    }
                }
                replaceMessages
            }

            withCluePrintln("send message and remove notification when read") {
                withCluePrintln("send receipt") {
                    rooms.forEachIndexed { index, room ->
                        startedClient2.client.api.room.setReadMarkers(room, null, notificationMessages[index])
                    }
                }

                withCluePrintln("receive removed notifications") {
                    checkNotifications { it.isEmpty() }
                }
            }
        }
    }

    private suspend inline fun sendMessageAndReceiveNotifications(
        rooms: List<RoomId>,
        roomsWithNotifications: Int,
        helloMessage: String,
        checkNotifications: suspend (check: (List<Notification>) -> Boolean) -> List<Notification>
    ): List<EventId> {
        val notificationMessages = withCluePrintln("send messages") {
            rooms.map { room ->
                startedClient1.client.room.sendMessage(room) {
                    text(helloMessage)
                    mentions(startedClient2.client.userId)
                }.let { transactionId ->
                    startedClient1.client.room.getOutbox(room).flatten()
                        .mapNotNull { it.find { it.transactionId == transactionId }?.eventId }
                        .firstWithTimeout()
                }
            }
        }
        log.debug { "sent messages $notificationMessages" }

        withCluePrintln("receive notifications") {
            checkNotifications { it.size == roomsWithNotifications }.also {
                it.forEach { notification ->
                    val messageNotification = notification.shouldBeInstanceOf<Notification.Message>().timelineEvent
                    messageNotification.eventId shouldBeIn notificationMessages.take(roomsWithNotifications)
                    messageNotification.content?.getOrNull()
                        .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                        .body shouldBe helloMessage
                }
            }
        }
        return notificationMessages
    }

    @Test
    @Suppress("DEPRECATION")
    fun testPushNotificationForNormalMessageLegacy(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val notifications = startedClient2.client.notification.getNotifications()
                .scan(listOf<NotificationService.Notification>()) { old, new -> old + new }
                .stateIn(scope)

            val room = startedClient1.client.api.room.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()

            withCluePrintln("first notification") {
                notifications.firstWithTimeout { it.size == 1 }.getOrNull(0).shouldNotBeNull()
                    .event.shouldBeInstanceOf<ClientEvent.StateBaseEvent<*>>()
                    .content.shouldBeInstanceOf<MemberEventContent>().displayName shouldBe "user2"
            }

            startedClient2.client.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            startedClient2.client.api.room.joinRoom(room).getOrThrow()

            startedClient1.client.room.sendMessage(room) { text("Hello ${startedClient2.client.userId.full}!") }
            withCluePrintln("second notification") {
                notifications.firstWithTimeout { it.size == 3 }.getOrNull(2).shouldNotBeNull()
                    .event.content.shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                    .body shouldStartWith "Hello"
            }
        }
    }
}