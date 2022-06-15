package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.room.toFlowList
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
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
    private lateinit var store2: Store

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
        store2 = ExposedStoreFactory(database2, Dispatchers.IO, scope2)
            .createStore(createEventContentSerializerMappings(), createMatrixEventJson())

        val storeFactory1 = ExposedStoreFactory(database1, Dispatchers.IO, scope1)
        val storeFactory2 = object : StoreFactory {
            override suspend fun createStore(contentMappings: EventContentSerializerMappings, json: Json): Store {
                return store2
            }
        }

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
        client1.syncState.first { it == SyncState.RUNNING }
        client2.syncState.first { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        scope1.cancel()
        scope2.cancel()
    }

    @Test
    fun shouldStartEncryptedRoomAndSendMessages(): Unit = runBlocking {
        withTimeout(30_000) {
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

            client2.room.getLastTimelineEvent(room)
                .filterNotNull()
                .takeWhile { decryptedMessages.size < 3 }
                .collectLatest { lastTimelineEvent ->
                    var currentTimelineEvent = lastTimelineEvent
                    while (currentCoroutineContext().isActive && decryptedMessages.size < 3) {
                        val currentTimelineEventValue = currentTimelineEvent
                            .filterNotNull()
                            .filter { it.event.content !is EncryptedEventContent || it.content?.isSuccess == true }
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

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromEndOfTheTimeline(): Unit = runBlocking {
        withTimeout(30_000) {
            val room = client1.api.rooms.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.rooms.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.stopSync(true)

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.value?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.room.getTimelineEvents(startFrom, room)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .first()
                .mapNotNull { it.value?.removeUnsigned() }

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

            client2.startSync().getOrThrow()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync = client2.room.getTimelineEvents(startFrom, room)
                .toFlowList(MutableStateFlow(31), MutableStateFlow(31))
                .first()
                .mapNotNull { it.value?.removeUnsigned() }

            timelineFromGappySync shouldBe expectedTimeline
        }
    }

    @Test
    fun shouldHandleGappySyncsAndGetEventsFromStartOfTheTimeline(): Unit = runBlocking {
        withTimeout(30_000) {
            val room = client1.api.rooms.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.rooms.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.stopSync(true)

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.value?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync().getOrThrow()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync =
                client2.room.getTimelineEvents(expectedTimeline.last().eventId, room, direction = FORWARDS)
                    .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
                    .first { list -> list.any { it.value?.nextEventId == null } }
                    .mapNotNull { it.value?.removeUnsigned() }

            // drop because the first event may have a gap due to the FORWARDS direction
            timelineFromGappySync.reversed().dropLast(1) shouldBe expectedTimeline.dropLast(1)
        }
    }

    @OptIn(FlowPreview::class)
    @Test
    fun shouldHandleGappySyncsAndFillTimelineFromTheMiddle(): Unit = runBlocking {
        withTimeout(30_000) {
            val room = client1.api.rooms.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.rooms.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.stopSync(true)

            (0..29).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val startFrom = client1.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }?.value?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.getExpectedTimelineToBeginning(startFrom, room)

            client2.startSync().getOrThrow()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "29"
            }
            val timelineFromGappySync =
                client2.room.getTimelineEventsAround(
                    expectedTimeline[18].eventId,
                    room,
                    MutableStateFlow(100),
                    MutableStateFlow(100)
                ).debounce(100)
                    .first { list ->
                        list.size > 31
                                && list.mapNotNull { it.value }.any { it.previousEventId == null }
                                && list.mapNotNull { it.value }.any { it.nextEventId == null }
                    }.also { list ->
                        val last = list.last().value.shouldNotBeNull()
                        while (list.last().value?.gap != null)
                            client2.room.fillTimelineGaps(last.eventId, last.roomId)
                    }.mapNotNull { it.value?.removeUnsigned() }

            timelineFromGappySync shouldBe expectedTimeline
        }
    }

    private suspend fun MatrixClient.getExpectedTimelineToBeginning(
        startFrom: EventId,
        roomId: RoomId
    ) = room.getTimelineEvents(startFrom, roomId)
        .toFlowList(MutableStateFlow(100), MutableStateFlow(31))
        .first { list -> list.mapNotNull { it.value }.any { it.previousEventId == null } }
        .also { list ->
            val last = list.last().value.shouldNotBeNull()
            while (list.last().value?.gap != null)
                client1.room.fillTimelineGaps(last.eventId, last.roomId)
        }
        .mapNotNull { it.value?.removeUnsigned() }

    @Test
    fun shouldHandleCancelOfMatrixClient(): Unit = runBlocking {
        withTimeout(60_000) {
            val room = client1.api.rooms.createRoom(invite = setOf(client2.userId)).getOrThrow()
            client2.room.getById(room).first { it?.membership == INVITE }
            client2.api.rooms.joinRoom(room).getOrThrow()
            client2.room.getById(room).first { it?.membership == JOIN }

            client2.stopSync(true)

            (0..99).forEach {
                client1.room.sendMessage(room) { text(it.toString()) }
                delay(50) // give it time to sync back
            }
            val lastEvent = client1.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "99"
            }?.value?.eventId.shouldNotBeNull()
            val expectedTimeline = client1.room.getTimelineEvents(lastEvent, room)
                .toFlowList(MutableStateFlow(100), MutableStateFlow(100))
                .first()
                .mapNotNull { it.value?.removeUnsigned() }

            expectedTimeline shouldHaveSize 100

            val middleEvent = expectedTimeline[50].eventId

            client2.startSync().getOrThrow()
            client2.room.getLastTimelineEvent(room).first {
                val content = it?.value?.content?.getOrNull()
                content is RoomMessageEventContent && content.body == "99"
            }
            val job = scope2.launch {
                client2.room.getTimelineEvents(lastEvent, room, limitPerFetch = 100).collect()
            }
            store2.roomTimeline.get(middleEvent, room, scope2).filterNotNull().first()
            scope2.cancel()
            job.join()

            val newStore2 = ExposedStoreFactory(database2, Dispatchers.IO, scope2)
                .createStore(createEventContentSerializerMappings(), createMatrixEventJson())
            newStore2.roomTimeline.get(middleEvent, room) shouldBe null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun TimelineEvent.removeUnsigned(): TimelineEvent? {
        return when (val event = event) {
            is Event.MessageEvent -> copy(event = event.copy(unsigned = null))
            is Event.StateEvent -> copy(event = (event as Event.StateEvent<Nothing>).copy(unsigned = null))
            else -> this
        }
    }
}