package net.folivo.trixnity.client.integrationtests

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
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.flattenValues
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.room.getTimeline
import net.folivo.trixnity.client.room.getTimelineEventsAround
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class TimelineEventIT {

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

        val repositoriesModule1 = createExposedRepositoriesModule(database1)
        val repositoriesModule2 = createExposedRepositoriesModule(database2)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule1,
            mediaStore = InMemoryMediaStore(),
            getLoginInfo = { it.register("user1", password) }
        ) {
            name = "client1"
        }.getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule2,
            mediaStore = InMemoryMediaStore(),
            getLoginInfo = { it.register("user2", password) }
        ) {
            name = "client2"
        }.getOrThrow()
        client1.startSync()
        client2.startSync()
        client1.syncState.first { it == SyncState.RUNNING }
        client2.syncState.first { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        runBlocking {
            client1.stop()
            client2.stop()
        }
    }

    @Test
    fun shouldStartEncryptedRoomAndSendMessages(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client1.room.sendMessage(room) { text("Hello!") }
            client1.room.getById(room).first { it?.encrypted == true }
            client1.room.waitForOutboxSent()

            val collectMessages = async {
                client2.room.getTimelineEventsFromNowOn()
                    .filter { it.roomId == room }
                    .filter { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                    .take(3)
            }

            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.encrypted == true }

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
            val client = MatrixClient.loginWith(
                baseUrl = baseUrl,
                repositoriesModule = createExposedRepositoriesModule(database),
                mediaStore = InMemoryMediaStore(),
                getLoginInfo = { it.register("user", password) }
            ) {
                storeTimelineEventContentUnencrypted = true
            }.getOrThrow()
            client.startSync()
            val room = client.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client.room.sendMessage(room) { text("dino") }

            val eventId = client.room.getLastTimelineEvent(room).flatMapLatest { it ?: flowOf(null) }
                .first { it?.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                ?.eventId
                .shouldNotBeNull()

            client.stopSync(true)
            client.stop()

            val exposedTimelineEvent = object : Table("room_timeline_event") {
                val roomId = varchar("room_id", length = 255)
                val eventId = varchar("event_id", length = 255)
                override val primaryKey = PrimaryKey(roomId, this.eventId)
                val value = text("value")
            }
            newSuspendedTransaction(Dispatchers.IO, database) {
                val result = exposedTimelineEvent.select {
                    exposedTimelineEvent.eventId.eq(eventId.full) and exposedTimelineEvent.roomId.eq(room.full)
                }.firstOrNull()?.get(exposedTimelineEvent.value).shouldNotBeNull()
                "dino".toRegex().findAll(result).count() shouldBe 1
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun shouldNotSaveUnencryptedTimelineEvent(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(180_000) {
            val database = newDatabase()
            val client = MatrixClient.loginWith(
                baseUrl = baseUrl,
                repositoriesModule = createExposedRepositoriesModule(database),
                mediaStore = InMemoryMediaStore(),
                getLoginInfo = { it.register("user", password) }
            ) {
                storeTimelineEventContentUnencrypted = false
            }.getOrThrow()
            client.startSync()
            val room = client.api.room.createRoom(
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            client.room.sendMessage(room) { text("dino") }

            val eventId = client.room.getLastTimelineEvent(room).flatMapLatest { it ?: flowOf(null) }
                .first { it?.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
                ?.eventId
                .shouldNotBeNull()

            client.stopSync(true)
            client.stop()

            val exposedTimelineEvent = object : Table("room_timeline_event") {
                val roomId = varchar("room_id", length = 255)
                val eventId = varchar("event_id", length = 255)
                override val primaryKey = PrimaryKey(roomId, this.eventId)
                val value = text("value")
            }
            newSuspendedTransaction(Dispatchers.IO, database) {
                exposedTimelineEvent.select {
                    exposedTimelineEvent.eventId.eq(eventId.full) and exposedTimelineEvent.roomId.eq(room.full)
                }.firstOrNull()?.get(exposedTimelineEvent.value).shouldNotContain("dino")
            }
        }
    }

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromEndOfTheTimeline(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.cancelSync(true)

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.first()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.room.getTimelineEvents(room, startFrom)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .first()
                .mapNotNull { it.first().removeUnsigned() }

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
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync = client2.room.getTimelineEvents(room, startFrom)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .first()
                .mapNotNull { it.first().removeUnsigned() }

            timelineFromGappySync shouldBe expectedTimeline
        }
    }

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromStartOfTheTimeline(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }
            client2.cancelSync(true)
            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.first()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync =
                client2.room.getTimelineEvents(room, expectedTimeline.last().eventId, direction = FORWARDS)
                    .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
                    .first { list -> list.any { it.first().nextEventId == null } }
                    .mapNotNull { it.first().removeUnsigned() }

            // drop because the first event may have a gap due to the FORWARDS direction
            timelineFromGappySync.reversed().dropLast(1) shouldBe expectedTimeline.dropLast(1)
        }
    }

    @OptIn(FlowPreview::class)
    @Test
    fun shouldHandleGappySyncsAndFillTimelineFromTheMiddle(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.cancelSync(true)

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.first()?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.first()?.content?.getOrNull()
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
                    .first { list ->
                        list.size > 31
                                && list.map { it.first() }.any { it.previousEventId == null }
                                && list.map { it.first() }.any { it.nextEventId == null }
                    }.also { list ->
                        val last = list.last().first().shouldNotBeNull()
                        while (list.last().first().gap != null)
                            client2.room.fillTimelineGaps(last.roomId, last.eventId)
                    }.mapNotNull { it.first().removeUnsigned() }

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
            }.first { it } // wait for sync

            val newRoom = client1.api.room.upgradeRoom(oldRoom, "10").getOrThrow()
            client1.room.sendMessage(newRoom) { text("hi new") }
            client1.room.getLastTimelineEvent(newRoom).filterNotNull().flatMapLatest { timelineEventFlow ->
                timelineEventFlow.map { it.content?.getOrNull() is RoomMessageEventContent.TextBased.Text }
            }.first { it } // wait for sync

            val timelineFromOldRoom =
                client1.room.getTimeline(oldRoom).apply {
                    init(
                        client1.room.getState<CreateEventContent>(oldRoom).first()?.idOrNull.shouldNotBeNull(),
                        configAfter = { maxSize = 20 })
                }
            val timelineFromNewRoom =
                client1.room.getTimeline(newRoom).apply {
                    init(client1.room.getById(newRoom).first()?.lastEventId.shouldNotBeNull())
                    loadBefore { maxSize = 20 }
                }

            timelineFromOldRoom.state.first().elements.map { it.first().eventId } shouldBe
                    timelineFromNewRoom.state.first().elements.map { it.first().eventId }

            client1.room.getAll().flattenValues(filterUpgradedRooms = true).first()
                .map { it.roomId } shouldNotContain oldRoom
        }
    }

    private suspend fun MatrixClient.getExpectedTimelineToBeginning(
        startFrom: EventId,
        roomId: RoomId
    ) = room.getTimelineEvents(roomId, startFrom)
        .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
        .first { list -> list.map { it.first() }.any { it.previousEventId == null } }
        .also { list ->
            val last = list.last().first().shouldNotBeNull()
            while (list.last().first().gap != null)
                client1.room.fillTimelineGaps(last.roomId, last.eventId)
        }
        .mapNotNull { it.first().removeUnsigned() }

    @Suppress("UNCHECKED_CAST")
    private fun TimelineEvent.removeUnsigned(): TimelineEvent? {
        return when (val event = event) {
            is MessageEvent -> copy(event = event.copy(unsigned = null))
            is StateEvent -> copy(event = (event as StateEvent<Nothing>).copy(unsigned = null))
            else -> this
        }
    }
}