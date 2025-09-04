package net.folivo.trixnity.client.notification

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class NotificationServiceTest : TrixnityBaseTest() {
    private val userId = UserId("user1", "localhost")
    private val roomId1 = RoomId("!room1:localhost")
    private val roomId2 = RoomId("!room2:localhost")
    private val notification1 = StoredNotification.Message(
        roomId = roomId1,
        eventId = eventId(1),
        sortKey = "s1",
        actions = setOf(PushAction.Notify)
    )
    private val notification2 = StoredNotification.Message(
        roomId = roomId1,
        eventId = eventId(2),
        sortKey = "s2",
        actions = setOf(PushAction.Notify)
    )

    private val notification3 =
        StoredNotification.State(
            roomId = roomId1,
            eventId = eventId(3),
            type = "m.room.member",
            stateKey = userId.full + "-3",
            sortKey = "s3",
            actions = setOf(PushAction.Notify)
        )

    private val roomService = RoomServiceMock().apply {
        scheduleSetup {
            returnGetTimelineEvent = flowOf()
            returnGetTimelineEventList = null
        }
    }
    private val roomStateStore = getInMemoryRoomStateStore { deleteAll() }
    private val accountStore = getInMemoryAccountStore {
        deleteAll()
        updateAccount {
            Account(
                olmPickleKey = "",
                baseUrl = "http://localhost",
                userId = userId,
                deviceId = "device",
                accessToken = "access_token",
                refreshToken = null,
                syncBatchToken = "sync_token",
                filterId = "filter_id",
                backgroundFilterId = "background_filter_id",
                displayName = "display_name",
                avatarUrl = "mxc://localhost/123456",
            )
        }
    }
    private val notificationStore = getInMemoryNotificationStore { deleteAll() }
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()
    private val matrixClientStarted = MatrixClientStarted()
    private val apiConfig = PortableMockEngineConfig()
    private val config = MatrixClientConfiguration().apply {
        scheduleSetup {
            enableExternalNotifications = false
        }
    }

    private fun TestScope.cut() = NotificationServiceImpl(
        api = mockMatrixClientServerApiClient(apiConfig),
        roomStateStore = roomStateStore,
        accountStore = accountStore,
        roomService = roomService,
        notificationStore = notificationStore,
        globalAccountDataStore = globalAccountDataStore,
        eventContentSerializerMappings = DefaultEventContentSerializerMappings,
        matrixClientStarted = matrixClientStarted,
        config = config,
        eventsToNotificationUpdates = object : EventsToNotificationUpdates {
            override suspend fun invoke(
                roomId: RoomId,
                eventFlow: Flow<ClientEvent<*>>,
                pushRules: List<PushRule>,
                existingNotifications: Map<String, String>,
                removeStale: Boolean
            ): List<StoredNotificationUpdate> = listOf()
        },
        coroutineScope = backgroundScope,
    )

    private fun eventId(index: Int) = EventId($$"$e$$index")

    private fun someTimelineEvent(index: Int) = TimelineEvent(
        ClientEvent.RoomEvent.MessageEvent<MessageEventContent>(
            content = Text("hi $index"),
            id = eventId(index),
            roomId = roomId1,
            sender = userId,
            originTimestamp = index.toLong(),
            unsigned = null,
        )
    )

    private fun someStateEvent(index: Int) = ClientEvent.RoomEvent.StateEvent(
        content = MemberEventContent(membership = Membership.JOIN),
        id = eventId(index),
        roomId = roomId1,
        sender = userId,
        originTimestamp = 1234,
        stateKey = userId.full + "-$index",
        unsigned = null,
    )

    @Test
    fun `getAll - return empty`() = runTest {
        cut().getAll().first().shouldBeEmpty()
    }

    @Test
    fun `getAll - return sorted notifications`() = runTest {
        notificationStore.save(notification2)
        notificationStore.save(notification1)
        notificationStore.save(notification3)
        roomService.returnGetTimelineEventList = mutableListOf(
            flowOf(someTimelineEvent(1)),
            flowOf(someTimelineEvent(2)),
        )
        roomStateStore.save(someStateEvent(3))
        cut().getAll().first().map { it.first() } shouldBe listOf(
            Notification.Message(
                id = notification1.id,
                sortKey = "s1",
                actions = setOf(PushAction.Notify),
                dismissed = false,
                timelineEvent = someTimelineEvent(1)
            ),
            Notification.Message(
                id = notification2.id,
                sortKey = "s2",
                actions = setOf(PushAction.Notify),
                dismissed = false,
                timelineEvent = someTimelineEvent(2)
            ),
            Notification.State(
                id = notification3.id,
                sortKey = "s3",
                actions = setOf(PushAction.Notify),
                dismissed = false,
                stateEvent = someStateEvent(3)
            ),
        )
    }

    @Test
    fun `getById - return message notifications`() = runTest {
        notificationStore.save(notification1)
        roomService.returnGetTimelineEventList = mutableListOf(
            flowOf(someTimelineEvent(1)),
        )
        cut().getById(notification1.id).first() shouldBe
                Notification.Message(
                    id = notification1.id,
                    sortKey = "s1",
                    actions = setOf(PushAction.Notify),
                    dismissed = false,
                    timelineEvent = someTimelineEvent(1)
                )
    }

    @Test
    fun `getById - return state notifications`() = runTest {
        notificationStore.save(notification3)
        roomStateStore.save(someStateEvent(3))

        cut().getById(notification3.id).first() shouldBe
                Notification.State(
                    id = notification3.id,
                    sortKey = "s3",
                    actions = setOf(PushAction.Notify),
                    dismissed = false,
                    stateEvent = someStateEvent(3)
                )
    }

    @Test
    fun `getById - message not found - return null notification`() = runTest {
        notificationStore.save(notification1)
        roomService.returnGetTimelineEventList = mutableListOf(
            flowOf(null),
        )
        cut().getById(notification1.id).first() shouldBe null
    }

    @Test
    fun `getById - state not found - return null notification`() = runTest {
        notificationStore.save(notification3)
        cut().getById(notification1.id).first() shouldBe null
    }

    @Test
    fun `getCount - for all rooms`() = runTest {
        notificationStore.save(notification2)
        notificationStore.save(notification1.copy(roomId = roomId2))
        notificationStore.save(notification3)

        cut().getCount().first() shouldBe 3
    }

    @Test
    fun dismiss() = runTest {
        notificationStore.save(notification1)
        notificationStore.save(notification2)

        cut().dismiss(notification1.id)
        notificationStore.getById(notification1.id).first()?.dismissed shouldBe true
    }

    @Test
    fun dismissAll() = runTest {
        notificationStore.save(notification1)
        notificationStore.save(notification2)

        cut().dismissAll()
        notificationStore.getById(notification1.id).first()?.dismissed shouldBe true
        notificationStore.getById(notification2.id).first()?.dismissed shouldBe true
    }

    @Test
    fun `getAllUpdates - remove queue`() = runTest {
        roomService.returnGetTimelineEventList = mutableListOf(
            flowOf(someTimelineEvent(1)),
            flowOf(someTimelineEvent(2)),
        )
        roomStateStore.save(someStateEvent(3))

        notificationStore.saveAllUpdates(
            listOf(
                StoredNotificationUpdate.New(
                    id = notification1.id,
                    sortKey = notification1.sortKey,
                    actions = setOf(PushAction.Notify),
                    content = StoredNotificationUpdate.Content.Message(notification1.roomId, notification1.eventId)
                ),
                StoredNotificationUpdate.Update(
                    id = notification2.id,
                    sortKey = notification2.sortKey,
                    actions = setOf(PushAction.Notify),
                    content = StoredNotificationUpdate.Content.Message(notification2.roomId, notification2.eventId)
                ),
            )
        )
        cut().onPush(roomId1, null) shouldBe false
        val resultChannel = Channel<NotificationUpdate>(0)
        backgroundScope.launch {
            cut().getAllUpdates().collect { resultChannel.send(it) }
        }
        val result1 = resultChannel.receive()
        result1 shouldBe NotificationUpdate.New(
            id = notification1.id,
            sortKey = notification1.sortKey,
            actions = setOf(PushAction.Notify),
            content = NotificationUpdate.Content.Message(
                someTimelineEvent(1)
            )
        )
        val result2 = resultChannel.receive()
        notificationStore.getAllUpdates().first().values.mapNotNull { it.first()?.id } shouldNotContain notification1.id
        result2 shouldBe NotificationUpdate.Update(
            id = notification2.id,
            sortKey = notification2.sortKey,
            actions = setOf(PushAction.Notify),
            content = NotificationUpdate.Content.Message(
                someTimelineEvent(2)
            )
        )
        val result3 = async { resultChannel.receive() }
        delay(10.milliseconds) // schedule async
        notificationStore.getAllUpdates().first().values.mapNotNull { it.first() }.shouldBeEmpty()

        notificationStore.saveAllUpdates(
            listOf(
                StoredNotificationUpdate.New(
                    id = notification3.id,
                    sortKey = notification3.sortKey,
                    actions = setOf(PushAction.Notify),
                    content = StoredNotificationUpdate.Content.State(
                        notification3.roomId,
                        notification3.eventId,
                        notification3.type,
                        notification3.stateKey,
                    )
                ),
            )
        )

        result3.await() shouldBe NotificationUpdate.New(
            id = notification3.id,
            sortKey = notification3.sortKey,
            actions = setOf(PushAction.Notify),
            content = NotificationUpdate.Content.State(
                someStateEvent(3)
            )
        )
        backgroundScope.launch { resultChannel.receive() }
        delay(10.milliseconds) // schedule launch
        notificationStore.getAllUpdates().first().values.mapNotNull { it.first() }.shouldBeEmpty()
    }

    @Test
    fun `onPush - without eventId - update state - new Push`() = runTest {
        cut().onPush(roomId1, null) shouldBe false
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.Push(roomId1)
        )
    }

    @Test
    fun `onPush - without eventId - update state - keep Push`() = runTest {
        notificationStore.updateState(roomId1) {
            StoredNotificationState.Push(roomId1)
        }
        cut().onPush(roomId1, null) shouldBe false
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.Push(roomId1)
        )
    }

    @Test
    fun `onPush - without eventId - update state - keep Remove`() = runTest {
        notificationStore.updateState(roomId1) {
            StoredNotificationState.Remove(roomId1)
        }
        cut().onPush(roomId1, null) shouldBe false
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.Remove(roomId1)
        )
    }

    @Test
    fun `onPush - without eventId - update state - update SyncWithTimeline`() = runTest {
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithTimeline(roomId1, false, setOf(), eventId(3), null, null)
        }
        cut().onPush(roomId1, null) shouldBe false
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(roomId1, true, setOf(), eventId(3), null, null)

        )
    }

    @Test
    fun `onPush - without eventId - update state - update SyncWithoutTimeline`() = runTest {
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, false)
        }
        cut().onPush(roomId1, null) shouldBe false
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithoutTimeline(roomId1, true),

            )
    }

    @Test
    fun `onPush - not found`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(null)
        cut().onPush(roomId1, eventId(1)) shouldBe false
    }

    @Test
    fun `onPush - found notification`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(null)
        notificationStore.save(notification1)
        cut().onPush(roomId1, eventId(1)) shouldBe true
    }

    @Test
    fun `onPush - found timelineEvent`() = runTest {
        roomService.returnGetTimelineEvent = flowOf(someTimelineEvent(1))
        cut().onPush(roomId1, eventId(1)) shouldBe true
    }

    @Test
    fun `processPush - wait for matrix client to be started`() = runTest {
        val result = async { cut().processPush() }
        delay(100.milliseconds)
        result.isActive shouldBe true
        matrixClientStarted.delegate.value = true
        delay(100.milliseconds)
        result.isActive shouldBe false
        result.await()
    }

    @Test
    fun `processPush - no push schedule left`() = runTest {
        matrixClientStarted.delegate.value = true
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, false)
        }
        cut().processPush()
    }

    @Test
    fun `processPush - start sync and wait for push processed`() = runTest {
        matrixClientStarted.delegate.value = true
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, true)
        }
        val cut = cut()
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(filter = "background_filter_id", timeout = 0)) {
                Sync.Response(nextBatch = "nextBatch")
            }
        }
        val result = async { cut.processPush() }
        delay(100.milliseconds)
        result.isActive shouldBe true
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, false)
        }
        delay(100.milliseconds)
        result.isActive shouldBe false
        result.await()
    }

    @Test
    fun `processPush - start sync and wait for updates processed`() = runTest {
        config.enableExternalNotifications = true
        matrixClientStarted.delegate.value = true
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, true)
        }
        notificationStore.updateUpdate("bla") { StoredNotificationUpdate.Remove("bla", roomId1) }
        val cut = cut()
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(filter = "background_filter_id", timeout = 0)) {
                Sync.Response(nextBatch = "nextBatch")
            }
        }
        val result = async { cut.processPush() }
        delay(100.milliseconds)
        result.isActive shouldBe true
        notificationStore.updateState(roomId1) {
            StoredNotificationState.SyncWithoutTimeline(roomId1, false)
        }
        delay(100.milliseconds)
        result.isActive shouldBe true

        notificationStore.updateUpdate("bla") { null }
        delay(100.milliseconds)
        result.isActive shouldBe false

        result.await()
    }
}