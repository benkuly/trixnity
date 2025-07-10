package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.membership
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class EncryptionIT {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var database1: Database
    private lateinit var database2: Database
    private lateinit var baseUrl: Url
    val password = "user$1passw0rd"

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = createInMemoryRepositoriesModule(),
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user1", password) }
        ) {
            name = "client1"
        }.getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = createInMemoryRepositoriesModule(),
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user2", password) }
        ) {
            name = "client2"
        }.getOrThrow()
        client1.startSync()
        client2.startSync()
        client1.syncState.firstWithTimeout { it == SyncState.RUNNING }
        client2.syncState.firstWithTimeout { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client1.close()
        client2.close()
    }

    @Test
    fun shouldEncryptOnJoin(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            withClue("ensure, that both already know each others devices") {
                val initialRoomId = client1.api.room.createRoom(
                    invite = setOf(client2.userId),
                    initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
                ).getOrThrow()
                client2.api.room.joinRoom(initialRoomId).getOrThrow()
                client1.user.getById(initialRoomId, client2.userId).firstWithTimeout { it?.membership == JOIN }
                client1.room.sendMessage(initialRoomId) { text("Share secret.") }
                client1.room.waitForOutboxSent()
            }

            val roomId = client1.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            withClue("prepare room") {
                client1.room.getById(roomId).firstWithTimeout { it?.encrypted == true }
                client1.room.sendMessage(roomId) { text("Keep secret!") }
                client1.room.waitForOutboxSent()

                client1.api.room.inviteUser(roomId, client2.userId).getOrThrow()
                client2.room.getById(roomId).firstWithTimeout { it?.membership == INVITE }
            }


            val collectMessages = async {
                client2.room.getTimelineEventsFromNowOn(decryptionTimeout = 2.seconds)
                    .filter { it.roomId == roomId }
                    .filter { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                    .take(1)
            }

            withClue("join room") {
                client2.api.room.joinRoom(roomId).getOrThrow()
                client1.user.getById(roomId, client2.userId).firstWithTimeout { it?.membership == JOIN }
            }

            eventually(4.seconds) {
                client1.di.get<OlmCryptoStore>().getOutboundMegolmSession(roomId).shouldNotBeNull()
                    .newDevices.keys.shouldContain(client2.userId)
            }

            client1.room.sendMessage(roomId) { text("Share secret.") }
            collectMessages.await().firstWithTimeout().content?.getOrThrow()
                .shouldNotBeNull()
                .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                .body shouldBe "Share secret."
        }
    }

    @Test
    fun shouldMassivelyDecrypt(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(60_000) {
            val roomId = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client1.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
            client1.stopSync()
            client1.closeSuspending()

            client2.api.room.joinRoom(roomId).getOrThrow()
            client2.room.getById(roomId).firstWithTimeout { it?.membership == JOIN }
            client2.stopSync()
            client2.closeSuspending()


            val clientFromStoreDatabase = newDatabase()
            MatrixClient.login(
                baseUrl = baseUrl,
                identifier = IdentifierType.User("user1"),
                password = password,
                repositoriesModule = createExposedRepositoriesModule(clientFromStoreDatabase),
                mediaStoreModule = createInMemoryMediaStoreModule(),
                deviceId = "stored client",
            ).getOrThrow().apply {
                syncOnce().getOrThrow()
                close()
            }
            val clientFromLogin = run {
                val database = newDatabase()
                MatrixClient.login(
                    baseUrl = baseUrl,
                    identifier = IdentifierType.User("user1"),
                    password = password,
                    repositoriesModule = createExposedRepositoriesModule(database),
                    mediaStoreModule = createInMemoryMediaStoreModule(),
                    deviceId = "loggedin client",
                ).getOrThrow().apply {
                    syncOnce().getOrThrow()
                }
            }

            val repeatCount = 4
            val messageCount = 10
            repeat(repeatCount) { iteration ->
                println(
                    """
                    ######
                    ###### iteration $iteration ######
                    ######
                """.trimIndent()
                )
                val clientFromStore = run {
                    MatrixClient.fromStore(
                        repositoriesModule = createExposedRepositoriesModule(clientFromStoreDatabase),
                        mediaStoreModule = createInMemoryMediaStoreModule(),
                    ).getOrThrow()
                }
                checkNotNull(clientFromStore)
                withClue("send messages") {
                    coroutineScope {
                        launch {
                            repeat(messageCount) { i ->
                                val senderClient = MatrixClient.login(
                                    baseUrl = baseUrl,
                                    identifier = IdentifierType.User("user2"),
                                    password = password,
                                    repositoriesModule = createInMemoryRepositoriesModule(),
                                    mediaStoreModule = createInMemoryMediaStoreModule(),
                                    deviceId = "sender client ($iteration - $i)",
                                ) {
                                    name = "sender client ($iteration - $i)"
                                }.getOrThrow()
                                senderClient.syncOnce().getOrThrow() // initial sync
                                senderClient.startSync()
                                senderClient.room.sendMessage(roomId) { text("message ($iteration - $i)") }
                                senderClient.room.waitForOutboxSent()
                                senderClient.stopSync()
                                senderClient.closeSuspending()
                            }
                        }
                    }
                }

                println(
                    """
                    ######
                    ###### sync receiver client ######
                    ######
                """.trimIndent()
                )
                clientFromStore.syncOnce().getOrThrow()
                clientFromLogin.syncOnce().getOrThrow()
                clientFromStore.startSync()
                clientFromLogin.startSync()

                withClue("decrypt messages from store") {
                    val lastEventId =
                        clientFromStore.room.getById(roomId).firstWithTimeout()?.lastEventId.shouldNotBeNull()
                    val receivedMessages = clientFromStore.room.getTimelineEvents(roomId, lastEventId)
                        .take(messageCount)
                        .toList()
                    receivedMessages shouldHaveSize messageCount
                    receivedMessages.reversed().forEachIndexed { i, messageFlow ->
                        val message = messageFlow.firstWithContent()
                        message.event.content.shouldBeInstanceOf<EncryptedMessageEventContent>()
                        message.content?.getOrThrow()
                            .shouldNotBeNull()
                            .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                            .body shouldBe "message ($iteration - $i)"
                    }
                }
                withClue("decrypt messages from login") {
                    val lastEventId =
                        clientFromLogin.room.getById(roomId).firstWithTimeout()?.lastEventId.shouldNotBeNull()
                    val receivedMessages = clientFromLogin.room.getTimelineEvents(roomId, lastEventId)
                        .take(messageCount)
                        .toList()
                    receivedMessages shouldHaveSize messageCount
                    receivedMessages.reversed().forEachIndexed { i, messageFlow ->
                        val message = messageFlow.firstWithContent()
                        message.event.content.shouldBeInstanceOf<EncryptedMessageEventContent>()
                        message.content?.getOrThrow()
                            .shouldNotBeNull()
                            .shouldBeInstanceOf<RoomMessageEventContent.TextBased.Text>()
                            .body shouldBe "message ($iteration - $i)"
                    }
                }
                clientFromStore.stopSync()
                clientFromLogin.stopSync()
                clientFromStore.closeSuspending()
            }
        }
    }
}