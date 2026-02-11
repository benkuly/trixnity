package de.connect2x.trixnity.client.notification

import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.client.store.StoredNotificationState.SyncWithTimeline.IsRead
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.StoredNotificationUpdate.Content
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.idOrNull
import de.connect2x.trixnity.core.model.events.m.PushRulesEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.*
import kotlin.test.Test

class NotificationEventHandlerProcessNotificationStateTest : TrixnityBaseTest() {
    private val userId = UserId("user1", "localhost")
    private val roomId1 = RoomId("!room1:localhost")
    private val roomId2 = RoomId("!room2:localhost")
    private val notification1 = StoredNotification.Message("s", roomId1, EventId("1"), setOf())
    private val notification2 = StoredNotification.Message("s", roomId2, EventId("2"), setOf())

    private val roomService = RoomServiceMock().apply {
        scheduleSetup {
            returnGetTimelineEvents = flowOf()
            getTimelineEventConfig = null
        }
    }
    private val roomStore = getInMemoryRoomStore { deleteAll() }
    private val roomStateStore = getInMemoryRoomStateStore { deleteAll() }
    private val roomUserStore = getInMemoryRoomUserStore { deleteAll() }
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore { deleteAll() }
    private val notificationStore = getInMemoryNotificationStore { deleteAll() }
    private val config = MatrixClientConfiguration().apply {
        scheduleSetup {
            enableExternalNotifications = false
        }
    }

    private class EventsToNotificationUpdatesMock() : EventsToNotificationUpdates {
        var notificationUpdates = listOf<StoredNotificationUpdate>()
        var events: List<EventId> = listOf()
        var removeStale: Boolean = false
        override suspend fun invoke(
            roomId: RoomId,
            eventFlow: Flow<ClientEvent<*>>,
            pushRules: List<PushRule>,
            existingNotifications: Map<String, String>,
            removeStale: Boolean
        ): List<StoredNotificationUpdate> {
            this.events = eventFlow.toList().mapNotNull { it.idOrNull }
            this.removeStale = removeStale
            return notificationUpdates
        }
    }

    private val eventsToNotificationUpdates = EventsToNotificationUpdatesMock().apply {
        scheduleSetup {
            notificationUpdates = listOf()
            events = listOf()
            removeStale = false
        }
    }

    private val cut = NotificationEventHandler(
        userInfo = UserInfo(UserId("us", "server"), "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api = mockMatrixClientServerApiClient(),
        roomService = roomService,
        roomStore = roomStore,
        roomStateStore = roomStateStore,
        roomUserStore = roomUserStore,
        globalAccountDataStore = globalAccountDataStore,
        notificationStore = notificationStore,
        keyStore = getInMemoryKeyStore(),
        eventsToNotificationUpdates = eventsToNotificationUpdates,
        currentSyncState = CurrentSyncState(MutableStateFlow(SyncState.RUNNING)),
        transactionManager = TransactionManagerMock(),
        eventContentSerializerMappings = EventContentSerializerMappings.default,
        config = config,
        coroutineScope = testScope.backgroundScope,
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

    private suspend fun processNotificationStateWith(
        notificationState: StoredNotificationState,
        notifications: List<StoredNotification> = listOf(
            notification1,
            notification2
        ),
        timeline: List<TimelineEvent> = listOf(
            someTimelineEvent(3),
            someTimelineEvent(2),
            someTimelineEvent(1),
            someTimelineEvent(0),
        ),
    ) {
        notifications.forEach { notificationStore.save(it) }
        notificationStore.updateState(roomId1) { notificationState }
        roomService.returnGetTimelineEvents = timeline.map { flowOf(it) }.asFlow()
        cut.processNotificationState(
            notificationState,
            NotificationEventHandler.PushRulesCache(PushRulesEventContent())
        )
    }

    @Test
    fun `Push - do nothing`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.Push(roomId1)
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.Push(roomId1)
        )
    }

    @Test
    fun `Read - remove notifications and state`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.Read(roomId1)
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(notification2)
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf()
    }

    @Test
    fun `Read - enableExternalNotifications - remove notifications and state with updates`() = runTest {
        config.enableExternalNotifications = true
        processNotificationStateWith(
            notificationState = StoredNotificationState.Read(roomId1)
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(notification2)
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf()
        notificationStore.getAllUpdates().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationUpdate.Remove(id = notification1.id, roomId = roomId1)
        )
    }

    @Test
    fun `SyncWithoutTimeline - notificationsDisabled - remove state and notifications`() = runTest {
        roomStateStore.save(someStateEvent(3))
        roomStateStore.save(someStateEvent(2))
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithoutTimeline(roomId = roomId1, true),
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(notification2)
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf()
        eventsToNotificationUpdates.events shouldBe listOf()
    }

    @Test
    fun `SyncWithoutTimeline - get updates with stale and remove state`() = runTest {
        roomStateStore.save(someStateEvent(3))
        roomStateStore.save(someStateEvent(2))
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithoutTimeline(roomId = roomId1, false),
        )

        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf()
        eventsToNotificationUpdates.events shouldBe listOf(
            eventId(3), eventId(2)
        )
        eventsToNotificationUpdates.removeStale shouldBe true
    }


    @Test
    fun `SyncWithoutTimeline - enableExternalNotifications - save all notifications`() = runTest {
        eventsToNotificationUpdates.notificationUpdates = listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId1, eventId(10)),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(10)),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-9"),
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(9), "m.room.member", userId.full + "-9"),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId1, eventId(8)),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(8)),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-7"),
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(7), "m.room.member", userId.full + "-7"),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId1, eventId(6)),
                roomId = roomId1,
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-5"),
                roomId = roomId1,
            ),
        )
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithoutTimeline(roomId = roomId1, false),
            notifications = listOf(
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(8),
                    sortKey = "old-8",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(7),
                    type = "m.room.member",
                    stateKey = userId.full + "-7",
                    sortKey = "old-7",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(6),
                    sortKey = "old-6",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(5),
                    type = "m.room.member",
                    stateKey = userId.full + "-5",
                    sortKey = "old-5",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
            )
        )

        notificationStore.getAll().first().values.map { it.first() } shouldContainExactlyInAnyOrder listOf(
            StoredNotification.Message(
                roomId = roomId1,
                eventId = eventId(10),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.State(
                roomId = roomId1,
                eventId = eventId(9),
                type = "m.room.member",
                stateKey = userId.full + "-9",
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.Message(
                roomId = roomId1,
                eventId = eventId(8),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.State(
                roomId = roomId1,
                eventId = eventId(7),
                type = "m.room.member",
                stateKey = userId.full + "-7",
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
            ),
        )
        notificationStore.getAllUpdates().first().shouldBeEmpty()
    }

    @Test
    fun `SyncWithoutTimeline - enableExternalNotifications - save all notification updates`() = runTest {
        config.enableExternalNotifications = true
        eventsToNotificationUpdates.notificationUpdates = listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId1, eventId(10)),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(10)),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-9"),
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(9), "m.room.member", userId.full + "-9"),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId1, eventId(8)),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(8)),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-7"),
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(7), "m.room.member", userId.full + "-7"),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId1, eventId(6)),
                roomId = roomId1,
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-5"),
                roomId = roomId1,
            ),
        )
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithoutTimeline(roomId = roomId1, false),
            notifications = listOf(
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(8),
                    sortKey = "old-8",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(7),
                    type = "m.room.member",
                    stateKey = userId.full + "-7",
                    sortKey = "old-7",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(6),
                    sortKey = "old-6",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(5),
                    type = "m.room.member",
                    stateKey = userId.full + "-5",
                    sortKey = "old-5",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
            )
        )

        notificationStore.getAllUpdates().first().values.map { it.first() } shouldContainExactlyInAnyOrder
                eventsToNotificationUpdates.notificationUpdates
    }

    @Test
    fun `SyncWithTimeline - notificationsDisabled - delete notifications and update state`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(12),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(notification2)
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - notificationsDisabled - isRead - delete notifications and remove state`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(12),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.TRUE,
            )
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(notification2)
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf()
    }

    @Test
    fun `SyncWithTimeline - notificationsDisabled - isRead needsCheck - remove state`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(1),
                lastProcessedEventId = eventId(2),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE_BUT_CHECK,
            )
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf()
    }

    @Test
    fun `SyncWithTimeline - notificationsDisabled - isRead needsCheck - FALSE`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE_BUT_CHECK,
            )
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = true,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - last event is last processed event - do nothing`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.TRUE,
            )
        )

        notificationStore.getAll().first().values.map { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.TRUE,
            )
        )
        eventsToNotificationUpdates.removeStale shouldBe false
    }

    @Test
    fun `SyncWithTimeline - expectedMaxNotificationCount 0 - do nothing`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 0,
                isRead = IsRead.TRUE,
            ),
            notifications = listOf()
        )

        eventsToNotificationUpdates.events shouldBe listOf()
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 0,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - expected max notification count is 0 - isRead needs check - take all`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = null,
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE_BUT_CHECK,
            ),
            notifications = listOf()
        )

        eventsToNotificationUpdates.events shouldBe listOf(eventId(3), eventId(2))
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - expected max notification count is 0 - process reset - take all`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = null,
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            ),
            notifications = listOf(notification1)
        )

        eventsToNotificationUpdates.events shouldBe listOf(eventId(3), eventId(2))
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - expected max notification count is 0 - stored notifications - take all`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            ),
            notifications = listOf(notification1)
        )

        eventsToNotificationUpdates.events shouldBe listOf(eventId(3), eventId(2))
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - expected max notification count is null - take all`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            ),
            notifications = listOf(notification2)
        )

        eventsToNotificationUpdates.events shouldBe listOf(eventId(3), eventId(2))
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = null,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - last processed event was reset - ask for removing existing notifications`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = null,
                expectedMaxNotificationCount = 2,
                isRead = IsRead.CHECK,
            ),
        )

        eventsToNotificationUpdates.removeStale shouldBe true
    }

    @Test
    fun `SyncWithTimeline - no stored notifications - get notifications until expected max notification count`() =
        runTest {
            processNotificationStateWith(
                notificationState = StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    notificationsDisabled = false,
                    readReceipts = setOf(eventId(0)),
                    lastEventId = eventId(3),
                    lastRelevantEventId = null,
                    lastProcessedEventId = eventId(0),
                    expectedMaxNotificationCount = 2,
                    isRead = IsRead.TRUE,
                ),
                notifications = listOf(),
            )

            roomService.getTimelineEventConfig?.maxSize shouldBe 2
        }

    @Test
    fun `SyncWithTimeline - no stored notifications - no limit when unchecked read marker`() =
        runTest {
            processNotificationStateWith(
                notificationState = StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    notificationsDisabled = false,
                    readReceipts = setOf(eventId(0)),
                    lastEventId = eventId(3),
                    lastRelevantEventId = null,
                    lastProcessedEventId = eventId(0),
                    expectedMaxNotificationCount = 2,
                    isRead = IsRead.TRUE_BUT_CHECK,
                ),
                notifications = listOf(),
            )

            roomService.getTimelineEventConfig?.maxSize shouldBe null
        }

    @Test
    fun `SyncWithTimeline - no process - get notifications until expected max notification count`() =
        runTest {
            processNotificationStateWith(
                notificationState = StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    notificationsDisabled = false,
                    readReceipts = setOf(eventId(0)),
                    lastEventId = eventId(3),
                    lastRelevantEventId = null,
                    lastProcessedEventId = null,
                    expectedMaxNotificationCount = 2,
                    isRead = IsRead.TRUE,
                ),
            )

            roomService.getTimelineEventConfig?.maxSize shouldBe 2
        }

    @Test
    fun `SyncWithTimeline - no stored notifications - get notifications until read marker`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(0),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.CHECK,
            ),
            notifications = listOf(),
        )

        eventsToNotificationUpdates.events shouldBe listOf(
            eventId(3), eventId(2)
        )
    }

    @Test
    fun `SyncWithTimeline - no stored notifications - get notifications until last processed`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(0)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.CHECK,
            ),
            notifications = listOf(),
        )

        eventsToNotificationUpdates.events shouldBe listOf(
            eventId(3), eventId(2)
        )
    }

    @Test
    fun `SyncWithTimeline - with stored notifications - get notifications until read marker`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(1)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(0),
                expectedMaxNotificationCount = 0,
                isRead = IsRead.CHECK,
            ),
        )

        eventsToNotificationUpdates.events shouldBe listOf(
            eventId(3), eventId(2)
        )
    }

    @Test
    fun `SyncWithTimeline - with stored notifications - get notifications until last processed`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(eventId(0)),
                lastEventId = eventId(3),
                lastRelevantEventId = null,
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 0,
                isRead = IsRead.CHECK,
            ),
        )

        eventsToNotificationUpdates.events shouldBe listOf(
            eventId(3), eventId(2)
        )
    }

    @Test
    fun `SyncWithTimeline - isRead needsCheck - TRUE`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(1),
                lastProcessedEventId = eventId(2),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE_BUT_CHECK,
            )
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(1),
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.TRUE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - isRead needsCheck - FALSE`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE_BUT_CHECK,
            )
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - isRead needs no check - keep`() = runTest {
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(1),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )
        notificationStore.getAllState().first().values.map { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(3),
                lastRelevantEventId = eventId(2),
                lastProcessedEventId = eventId(3),
                expectedMaxNotificationCount = 3,
                isRead = IsRead.FALSE,
            )
        )
    }

    @Test
    fun `SyncWithTimeline - save all notifications`() = runTest {
        eventsToNotificationUpdates.notificationUpdates = listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId1, eventId(10)),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(10)),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-9"),
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(9), "m.room.member", userId.full + "-9"),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId1, eventId(8)),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(8)),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-7"),
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(7), "m.room.member", userId.full + "-7"),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId1, eventId(6)),
                roomId = roomId1,
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-5"),
                roomId = roomId1,
            ),
        )
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(10),
                lastRelevantEventId = null,
                lastProcessedEventId = null,
                expectedMaxNotificationCount = 10,
                isRead = IsRead.CHECK,
            ),
            notifications = listOf(
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(8),
                    sortKey = "old-8",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(7),
                    type = "m.room.member",
                    stateKey = userId.full + "-7",
                    sortKey = "old-7",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(6),
                    sortKey = "old-6",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(5),
                    type = "m.room.member",
                    stateKey = userId.full + "-5",
                    sortKey = "old-5",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
            )
        )

        notificationStore.getAll().first().values.map { it.first() } shouldContainExactlyInAnyOrder listOf(
            StoredNotification.Message(
                roomId = roomId1,
                eventId = eventId(10),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.State(
                roomId = roomId1,
                eventId = eventId(9),
                type = "m.room.member",
                stateKey = userId.full + "-9",
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.Message(
                roomId = roomId1,
                eventId = eventId(8),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
            ),
            StoredNotification.State(
                roomId = roomId1,
                eventId = eventId(7),
                type = "m.room.member",
                stateKey = userId.full + "-7",
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
            ),
        )
        notificationStore.getAllUpdates().first().shouldBeEmpty()
    }

    @Test
    fun `SyncWithTimeline - enableExternalNotifications - save all notification updates`() = runTest {
        config.enableExternalNotifications = true
        eventsToNotificationUpdates.notificationUpdates = listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId1, eventId(10)),
                sortKey = "new-10",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(10)),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-9"),
                sortKey = "new-9",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(9), "m.room.member", userId.full + "-9"),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId1, eventId(8)),
                sortKey = "new-8",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId1, eventId(8)),
            ),
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-7"),
                sortKey = "new-7",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId1, eventId(7), "m.room.member", userId.full + "-7"),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId1, eventId(6)),
                roomId = roomId1,
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.State.id(roomId1, "m.room.member", userId.full + "-5"),
                roomId = roomId1,
            ),
        )
        processNotificationStateWith(
            notificationState = StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = true,
                notificationsDisabled = false,
                readReceipts = setOf(),
                lastEventId = eventId(10),
                lastRelevantEventId = null,
                lastProcessedEventId = null,
                expectedMaxNotificationCount = 10,
                isRead = IsRead.CHECK,
            ),
            notifications = listOf(
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(8),
                    sortKey = "old-8",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(7),
                    type = "m.room.member",
                    stateKey = userId.full + "-7",
                    sortKey = "old-7",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.Message(
                    roomId = roomId1,
                    eventId = eventId(6),
                    sortKey = "old-6",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
                StoredNotification.State(
                    roomId = roomId1,
                    eventId = eventId(5),
                    type = "m.room.member",
                    stateKey = userId.full + "-5",
                    sortKey = "old-5",
                    actions = setOf(PushAction.SetHighlightTweak()),
                ),
            )
        )

        notificationStore.getAllUpdates().first().values.map { it.first() } shouldContainExactlyInAnyOrder
                eventsToNotificationUpdates.notificationUpdates
    }
}