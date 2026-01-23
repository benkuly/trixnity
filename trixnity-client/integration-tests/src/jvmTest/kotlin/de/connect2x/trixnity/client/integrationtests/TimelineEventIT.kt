package de.connect2x.trixnity.client.integrationtests

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room.getState
import de.connect2x.trixnity.client.room.getTimeline
import de.connect2x.trixnity.client.room.getTimelineEventsAround
import de.connect2x.trixnity.client.room.message.replace
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.room.toFlowList
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.store.repository.inMemory
import de.connect2x.trixnity.client.store.roomId
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLogin
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.FORWARDS
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.idOrNull
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.INVITE
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.invoke
import de.connect2x.trixnity.core.serialization.events.messageOf
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.koin.dsl.module
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class TimelineEventIT : TrixnityBaseTest() {

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
        database1 = newDatabase()
        database2 = newDatabase()

        val repositoriesModule1 = RepositoriesModule.exposed(database1)
        val repositoriesModule2 = RepositoriesModule.exposed(database2)

        client1 = MatrixClient.create(
            repositoriesModule = repositoriesModule1,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user1", password)
            }.getOrThrow(),
            configuration = {
                name = "client1"
            },
        ).getOrThrow()
        client2 = MatrixClient.create(
            repositoriesModule = repositoriesModule2,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user2", password)
            }.getOrThrow(),
            configuration = {
                name = "client2"
            },
        ).getOrThrow()
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
    fun shouldBeAbleToReadMessagesBeforeJoin(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client1.room.getById(room).firstWithTimeout { it?.encrypted == true }
            client1.room.sendMessage(room) { text("Hello!") }
            client1.room.waitForOutboxSent()

            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }

            val lastEventId = client2.room.getById(room).map { it?.lastEventId }.filterNotNull().firstWithTimeout()
            val timeline = client2.room.getTimeline()
            timeline.init(room, lastEventId)
            timeline.state.firstWithTimeout().elements.map {
                it.map { it.content }.filterNotNull().firstWithTimeout().getOrThrow()
            }
                .any { it is RoomMessageEventContent.TextBased.Text && it.body == "Hello!" } shouldBe true
        }
    }

    @Test
    fun shouldHandleReplaceAndRedactions(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }

            val txnA = client2.room.sendMessage(room) { text("A") }
            val eventIdA = client2.room.getOutbox(room).flatten()
                .map { it.find { it.transactionId == txnA && it.eventId != null }?.eventId }.filterNotNull()
                .firstWithTimeout()
            val txnB = client2.room.sendMessage(room) {
                replace(eventIdA)
                text("B")
            }
            val eventIdB = client2.room.getOutbox(room).flatten()
                .map { it.find { it.transactionId == txnB && it.eventId != null }?.eventId }.filterNotNull()
                .firstWithTimeout()
            val txnC = client2.room.sendMessage(room) {
                replace(eventIdA)
                text("C")
            }
            val eventIdC = client2.room.getOutbox(room).flatten()
                .map { it.find { it.transactionId == txnC && it.eventId != null }?.eventId }.filterNotNull()
                .firstWithTimeout()
            val txnD = client2.room.sendMessage(room) {
                replace(eventIdA)
                text("D")
            }
            val eventIdD = client2.room.getOutbox(room).flatten()
                .map { it.find { it.transactionId == txnD && it.eventId != null }?.eventId }.filterNotNull()
                .firstWithTimeout()

            client1.room.getById(room).firstWithTimeout { it?.lastEventId == eventIdD }
            val coroutineScope = CoroutineScope(Dispatchers.Default)
            val timelineEvent = client1.room.getTimelineEvent(room, eventIdA).filterNotNull().stateIn(coroutineScope)
            withCluePrintln("wait for D") {
                timelineEvent.map { it.content?.getOrNull() }
                    .firstWithTimeout { (it as? RoomMessageEventContent.TextBased.Text)?.body == "D" }
            }
            client2.api.room.redactEvent(room, eventIdD).getOrThrow()
            withCluePrintln("wait for C") {
                timelineEvent.map { it.content?.getOrNull() }
                    .firstWithTimeout { (it as? RoomMessageEventContent.TextBased.Text)?.body == "C" }
            }
            client2.api.room.redactEvent(room, eventIdC).getOrThrow()
            withCluePrintln("wait for B") {
                timelineEvent.map { it.content?.getOrNull() }
                    .firstWithTimeout { (it as? RoomMessageEventContent.TextBased.Text)?.body == "B" }
            }
            client2.api.room.redactEvent(room, eventIdA).getOrThrow()
            withCluePrintln("wait for redact") {
                timelineEvent.map { it.content?.getOrNull() }
                    .firstWithTimeout { it is RedactedEventContent }
            }

            coroutineScope.cancel()
        }
    }

    @Test
    fun shouldStartEncryptedRoomAndSendMessages(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client1.room.getById(room).firstWithTimeout { it?.encrypted == true }
            client1.room.sendMessage(room) { text("Hello!") }
            client1.room.waitForOutboxSent()

            val collectMessages = async {
                client2.room.getTimelineEventsFromNowOn()
                    .filter { it.roomId == room }
                    .filter { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                    .take(3)
            }

            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.encrypted == true }

            client2.room.sendMessage(room) { text("Hello to you, too!") }
            client1.room.sendMessage(room) { text("How are you?") }

            collectMessages.await()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun shouldSaveUnencryptedTimelineEvent(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(180_000) {
            val database = newDatabase()
            val client = MatrixClient.create(
                repositoriesModule = RepositoriesModule.exposed(database),
                mediaStoreModule = MediaStoreModule.inMemory(),
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                    it.register("user", password)
                }.getOrThrow(),
                configuration = {
                    storeTimelineEventContentUnencrypted = true
                },
            ).getOrThrow()
            client.startSync()
            val room = client.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client.room.sendMessage(room) { text("dino") }

            val eventId = client.room.getLastTimelineEvent(room).flatMapLatest { it ?: flowOf(null) }
                .firstWithTimeout { it?.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                ?.eventId
                .shouldNotBeNull()

            client.stopSync()
            client.syncOnce().getOrThrow()
            client.closeSuspending()

            val exposedTimelineEvent = object : Table("room_timeline_event") {
                val roomId = varchar("room_id", length = 255)
                val eventId = varchar("event_id", length = 255)
                override val primaryKey = PrimaryKey(this.roomId, this.eventId)
                val value = text("value")
            }
            newSuspendedTransaction(Dispatchers.IO, database) {
                val result = exposedTimelineEvent.selectAll()
                    .where { exposedTimelineEvent.eventId.eq(eventId.full) and exposedTimelineEvent.roomId.eq(room.full) }
                    .firstOrNull()?.get(exposedTimelineEvent.value).shouldNotBeNull()
                "dino".toRegex().findAll(result).count() shouldBe 1
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun shouldNotSaveUnencryptedTimelineEvent(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(180_000) {
            val database = newDatabase()
            val client = MatrixClient.create(
                repositoriesModule = RepositoriesModule.exposed(database),
                mediaStoreModule = MediaStoreModule.inMemory(),
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                    it.register("user", password)
                }.getOrThrow(),
                configuration = {
                    storeTimelineEventContentUnencrypted = false
                },
            ).getOrThrow()
            client.startSync()
            val room = client.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client.room.getById(room).firstWithTimeout { it?.encrypted == true }
            client.room.sendMessage(room) { text("dino") }

            val eventId = client.room.getLastTimelineEvent(room).flatMapLatest { it ?: flowOf(null) }
                .firstWithTimeout { it?.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                ?.eventId
                .shouldNotBeNull()

            client.stopSync()
            client.closeSuspending()

            val exposedTimelineEvent = object : Table("room_timeline_event") {
                val roomId = varchar("room_id", length = 255)
                val eventId = varchar("event_id", length = 255)
                override val primaryKey = PrimaryKey(roomId, this.eventId)
                val value = text("value")
            }
            newSuspendedTransaction(Dispatchers.IO, database) {
                exposedTimelineEvent.selectAll()
                    .where { exposedTimelineEvent.eventId.eq(eventId.full) and exposedTimelineEvent.roomId.eq(room.full) }
                    .firstOrNull()?.get(exposedTimelineEvent.value).shouldNotContain("dino")
            }
        }
    }

    @Serializable
    class TestUnknownEventContent(
        val test: String
    ) : MessageEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        override fun copyWith(relatesTo: RelatesTo?) = this
    }

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromEndOfTheTimeline(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }

            client2.cancelSync()

            val baseUrl = URLBuilder(
                protocol = URLProtocol.HTTP,
                host = synapseDocker.host,
                port = synapseDocker.firstMappedPort
            ).build()
            val client3 = MatrixClient.create(
                repositoriesModule = RepositoriesModule.inMemory(),
                mediaStoreModule = MediaStoreModule.inMemory(),
                cryptoDriverModule = CryptoDriverModule.vodozemac(),
                authProviderData = MatrixClientAuthProviderData.classicLogin(
                    baseUrl = baseUrl,
                    identifier = IdentifierType.User("user1"),
                    password = "user$1passw0rd",
                ).getOrThrow(),
            ) {
                name = "client3"
                modulesFactories += {
                    module {
                        single<EventContentSerializerMappings> {
                            EventContentSerializerMappings.default + EventContentSerializerMappings {
                                messageOf<TestUnknownEventContent>("de.connect2x.test")
                            }
                        }
                    }
                }
            }.getOrThrow()

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                client3.api.room.sendMessageEvent(room, TestUnknownEventContent(it.toString()))
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.firstWithTimeout()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.room.getTimelineEvents(room, startFrom)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .firstWithTimeout()
                .mapNotNull { it.firstWithTimeout().removeUnsigned() }

            expectedTimeline shouldHaveSize 31

            expectedTimeline.dropLast(1).reversed().forEachIndexed { index, timelineEvent ->
                timelineEvent.content?.getOrNull()
                    .shouldBeInstanceOf<RoomMessageEventContent>().body shouldBe index.toString()
                if (index == 29) {
                    timelineEvent.gap.shouldBeInstanceOf<TimelineEvent.Gap.GapAfter>()
                } else {
                    timelineEvent.gap shouldBe null
                    timelineEvent.nextEventId shouldNotBe null
                }
                timelineEvent.previousEventId shouldNotBe null
            }

            client2.startSync()
            client2.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync = client2.room.getTimelineEvents(room, startFrom)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .firstWithTimeout()
                .mapNotNull { it.firstWithTimeout().removeUnsigned() }

            timelineFromGappySync shouldBe expectedTimeline
        }
    }

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromStartOfTheTimeline(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }
            client2.cancelSync()
            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.firstWithTimeout()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync()
            client2.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync =
                client2.room.getTimelineEvents(room, expectedTimeline.last().eventId, direction = FORWARDS)
                    .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
                    .firstWithTimeout { list -> list.any { it.firstWithTimeout().nextEventId == null } }
                    .mapNotNull { it.firstWithTimeout().removeUnsigned() }

            // drop because the first event may have a gap due to the FORWARDS direction
            timelineFromGappySync.reversed().dropLast(1) shouldBe expectedTimeline.dropLast(1)
        }
    }

    @OptIn(FlowPreview::class)
    @Test
    fun shouldHandleGappySyncsAndFillTimelineFromTheMiddle(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }

            client2.cancelSync()

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.firstWithTimeout()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync()
            client2.room.getLastTimelineEvent(room).firstWithTimeout {
                val content = it?.firstWithTimeout()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync =
                client2.room.getTimelineEventsAround(
                    room,
                    expectedTimeline[18].eventId,
                    maxSizeBefore = MutableStateFlow(100),
                    maxSizeAfter = MutableStateFlow(100)
                ).debounce(100)
                    .map { it.reversed() }
                    .firstWithTimeout { list ->
                        list.size > 31
                                && list.map { it.firstWithTimeout() }.any { it.previousEventId == null }
                                && list.map { it.firstWithTimeout() }.any { it.nextEventId == null }
                    }.also { list ->
                        val last = list.last().firstWithTimeout().shouldNotBeNull()
                        while (list.last().firstWithTimeout().gap != null)
                            client2.room.fillTimelineGaps(last.roomId, last.eventId)
                    }.mapNotNull { it.firstWithTimeout().removeUnsigned() }

            timelineFromGappySync shouldBe expectedTimeline
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun shouldFollowRoomUpgrades(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val oldRoom = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                roomVersion = "9"
            ).getOrThrow()
            client1.room.sendMessage(oldRoom) { text("hi old") }
            client1.room.getLastTimelineEvent(oldRoom).filterNotNull().flatMapLatest { timelineEventFlow ->
                timelineEventFlow.map { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
            }.firstWithTimeout { it } // wait for sync

            val newRoom = client1.api.room.upgradeRoom(oldRoom, "10").getOrThrow()
            client1.room.sendMessage(newRoom) { text("hi new") }
            client1.room.getLastTimelineEvent(newRoom).filterNotNull().flatMapLatest { timelineEventFlow ->
                timelineEventFlow.map { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
            }.firstWithTimeout { it } // wait for sync

            val timelineFromOldRoom =
                client1.room.getTimeline().apply {
                    init(
                        oldRoom,
                        client1.room.getState<CreateEventContent>(oldRoom)
                            .firstWithTimeout()?.idOrNull.shouldNotBeNull(),
                        configAfter = { maxSize = 20 })
                }.state.firstWithTimeout().elements.map { it.firstWithTimeout() }
                    .map { it.eventId to it.event.content::class.simpleName }
            println(
                """
                |# timeline from old room
                |${timelineFromOldRoom.joinToString("\n|")}
            """.trimMargin()
            )
            val timelineFromNewRoom =
                client1.room.getTimeline().apply {
                    init(newRoom, client1.room.getById(newRoom).firstWithTimeout()?.lastEventId.shouldNotBeNull())
                    loadBefore { maxSize = 20 }
                }.state.firstWithTimeout().elements.map { it.firstWithTimeout() }
                    .map { it.eventId to it.event.content::class.simpleName }
            println(
                """
                |# timeline from new room
                |${timelineFromOldRoom.joinToString("\n|")}
            """.trimMargin()
            )

            timelineFromOldRoom shouldBe timelineFromNewRoom

            client1.room.getAll().flattenValues(filterUpgradedRooms = true).firstWithTimeout()
                .map { it.roomId } shouldNotContain oldRoom
        }
    }

    private suspend fun MatrixClient.getExpectedTimelineToBeginning(
        startFrom: EventId,
        roomId: RoomId
    ) = room.getTimelineEvents(roomId, startFrom)
        .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
        .firstWithTimeout { list -> list.map { it.firstWithTimeout() }.any { it.previousEventId == null } }
        .also { list ->
            val last = list.last().firstWithTimeout().shouldNotBeNull()
            while (list.last().firstWithTimeout().gap != null)
                client1.room.fillTimelineGaps(last.roomId, last.eventId)
        }
        .mapNotNull { it.firstWithTimeout().removeUnsigned() }

    @Suppress("UNCHECKED_CAST")
    private fun TimelineEvent.removeUnsigned(): TimelineEvent? {
        return when (val event = event) {
            is MessageEvent -> copy(event = event.copy(unsigned = null))
            is StateEvent -> copy(event = (event as StateEvent<Nothing>).copy(unsigned = null))
        }
    }
}