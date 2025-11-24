package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.StoredNotification
import net.folivo.trixnity.client.store.StoredNotificationState
import net.folivo.trixnity.client.store.StoredNotificationUpdate
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.ServerDefaultPushRules
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class NotificationEventHandlerProcessSyncTest : TrixnityBaseTest() {
    private val userId = UserId("user1", "localhost")
    private val roomId1 = RoomId("!room1:localhost")
    private val roomId2 = RoomId("!room2:localhost")
    private val notification1 = StoredNotification.Message("s", roomId1, EventId("1"), setOf())
    private val notification2 = StoredNotification.Message("s", roomId2, EventId("2"), setOf())

    private val roomService = RoomServiceMock().apply {
        scheduleSetup {
            returnGetTimelineEvents = flowOf()
        }
    }
    private val roomStore = getInMemoryRoomStore { deleteAll() }
    private val roomStateStore = getInMemoryRoomStateStore { deleteAll() }
    private val roomUserStore = getInMemoryRoomUserStore { deleteAll() }
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore { deleteAll() }
    private val notificationStore = getInMemoryNotificationStore { deleteAll() }

    private class EventsToNotificationUpdatesMock() : EventsToNotificationUpdates {
        var notificationUpdates = listOf<StoredNotificationUpdate>()
        override suspend fun invoke(
            roomId: RoomId,
            eventFlow: Flow<ClientEvent<*>>,
            pushRules: List<PushRule>,
            existingNotifications: Map<String, String>,
            removeStale: Boolean
        ): List<StoredNotificationUpdate> = notificationUpdates
    }

    private val eventsToNotificationUpdates = EventsToNotificationUpdatesMock().apply {
        scheduleSetup { notificationUpdates = listOf() }
    }

    private val cut = NotificationEventHandler(
        userInfo = UserInfo(userId, "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api = mockMatrixClientServerApiClient(),
        roomService = roomService,
        roomStore = roomStore,
        roomStateStore = roomStateStore,
        roomUserStore = roomUserStore,
        globalAccountDataStore = globalAccountDataStore,
        notificationStore = notificationStore,
        eventsToNotificationUpdates = eventsToNotificationUpdates,
        transactionManager = TransactionManagerMock(),
        eventContentSerializerMappings = DefaultEventContentSerializerMappings,
        config = MatrixClientConfiguration()
    )

    private fun pushRulesEvent(updateOverride: (List<PushRule.Override>) -> List<PushRule.Override> = { it }) =
        ClientEvent.GlobalAccountDataEvent(
            PushRulesEventContent(
                ServerDefaultPushRules.pushRuleSet(userId).let {
                    it.copy(override = it.override.orEmpty().let(updateOverride))
                }
            )
        )

    private suspend fun processSyncWith(
        notifications: List<StoredNotification> = listOf(notification1, notification2),
        notificationStates: List<StoredNotificationState> = listOf(
            StoredNotificationState.SyncWithoutTimeline(roomId1),
            StoredNotificationState.SyncWithoutTimeline(roomId2)
        ),
        updatedRooms: Set<RoomId> = setOf(roomId1, roomId2),
        receipts: Map<RoomId, EventId> = mapOf(roomId1 to EventId("e1"), roomId2 to EventId("e1")),
        notificationCounts: Map<RoomId, Long> = mapOf(),
        pushRuleChange: Boolean = true,
        pushRuleOverride: (List<PushRule.Override>) -> List<PushRule.Override> = { it },
    ) {
        notifications.forEach { notification -> notificationStore.save(notification) }
        notificationStates.forEach { notificationState -> notificationStore.updateState(notificationState.roomId) { notificationState } }
        receipts.forEach { (roomId, receiptEventId) ->
            roomUserStore.updateReceipts(userId, roomId) {
                RoomUserReceipts(
                    roomId1, userId, mapOf(
                        ReceiptType.Read to RoomUserReceipts.Receipt(
                            receiptEventId,
                            ReceiptEventContent.Receipt(1234)
                        )
                    )
                )
            }
        }
        val pushRulesEvent = pushRulesEvent(pushRuleOverride)
        globalAccountDataStore.save(pushRulesEvent)
        cut.processSync(
            SyncEvents(
                Sync.Response(
                    "",
                    accountData = Sync.Response.GlobalAccountData(
                        events = listOfNotNull(
                            if (pushRuleChange) pushRulesEvent else null
                        )
                    ),
                    room = Sync.Response.Rooms(
                        join = updatedRooms.associateWith { roomId ->
                            val receipt = receipts[roomId]
                            Sync.Response.Rooms.JoinedRoom(
                                unreadNotifications = notificationCounts[roomId]?.let { notificationCount ->
                                    Sync.Response.Rooms.JoinedRoom.UnreadNotificationCounts(
                                        notificationCount = notificationCount
                                    )
                                },
                                ephemeral = Sync.Response.Rooms.JoinedRoom.Ephemeral(
                                    events = listOfNotNull(
                                        if (receipt != null) ClientEvent.EphemeralEvent(
                                            ReceiptEventContent(
                                                mapOf(
                                                    receipt to mapOf(
                                                        ReceiptType.Read to mapOf(
                                                            userId to ReceiptEventContent.Receipt(
                                                                1
                                                            )
                                                        )
                                                    )
                                                )

                                            )
                                        ) else null
                                    )
                                ),
                            )
                        }.let(::RoomMap)
                    )
                )
            )
        )
    }

    @Test
    fun `push rules disabled changed - schedule remove `() =
        runTest {
            processSyncWith(
                updatedRooms = setOf(),
                pushRuleOverride = {
                    it.map { if (it.ruleId == ServerDefaultPushRules.Master.id) it.copy(enabled = true) else it }
                }
            )

            notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
                notification1,
                notification2
            )
            notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
                StoredNotificationState.Remove(roomId1),
                StoredNotificationState.Remove(roomId2),
            )
        }

    @Test
    fun `push rules disabled without change - do nothing`() =
        runTest {
            processSyncWith(
                updatedRooms = setOf(),
                pushRuleChange = false,
                pushRuleOverride = {
                    it.map { if (it.ruleId == ServerDefaultPushRules.Master.id) it.copy(enabled = true) else it }
                })

            notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
                notification1,
                notification2
            )
            notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
                StoredNotificationState.SyncWithoutTimeline(roomId1),
                StoredNotificationState.SyncWithoutTimeline(roomId2),
            )
        }

    @Test
    fun `push rules disabled for room changed - schedule remove`() =
        runTest {
            processSyncWith(
                updatedRooms = setOf(roomId1),
                pushRuleOverride = {
                    it + PushRule.Override(
                        roomId1.full, false, true, conditions = setOf(
                            PushCondition.EventMatch("room_id", roomId1.full)
                        )
                    )
                }
            )
            notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
                notification1,
                notification2
            )
            notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
                StoredNotificationState.Remove(roomId1),
                StoredNotificationState.SyncWithoutTimeline(roomId2),
            )
        }

    @Test
    fun `push rules disabled for room without change - do nothing`() =
        runTest {
            processSyncWith(
                updatedRooms = setOf(roomId1),
                pushRuleChange = false,
                pushRuleOverride = {
                    it + PushRule.Override(
                        roomId1.full, false, true, conditions = setOf(
                            PushCondition.EventMatch("room_id", roomId1.full)
                        )
                    )
                }
            )

            notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
                notification1,
                notification2
            )
            notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
                StoredNotificationState.SyncWithoutTimeline(roomId1),
                StoredNotificationState.SyncWithoutTimeline(roomId2),
            )
        }

    @Test
    fun `no receipts for room - no timeline - set state`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = null) }
        processSyncWith(updatedRooms = setOf(roomId1), receipts = mapOf())

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithoutTimeline(roomId1),
            StoredNotificationState.SyncWithoutTimeline(roomId2),
        )
    }

    @Test
    fun `room is read - schedule remove`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e1")) }
        processSyncWith(updatedRooms = setOf(roomId1))

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.Remove(roomId1),
            StoredNotificationState.SyncWithoutTimeline(roomId2),
        )
    }

    @Test
    fun `with timeline - existing state - receipts changed`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e24")) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(
                StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    readReceipts = setOf(EventId("e0")),
                    lastEventId = EventId("e24"),
                    lastProcessedEventId = EventId("e23"),
                    expectedMaxNotificationCount = 1,
                )
            ),
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = false,
                readReceipts = setOf(EventId("e1")),
                lastEventId = EventId("e24"),
                lastProcessedEventId = null,
                expectedMaxNotificationCount = 1,
            )
        )
    }

    @Test
    fun `with timeline - existing state - receipts not changed - keep process`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e24")) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(
                StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    readReceipts = setOf(EventId("e1")),
                    lastEventId = EventId("e24"),
                    lastProcessedEventId = EventId("e23"),
                    expectedMaxNotificationCount = 1,
                )
            ),
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = false,
                readReceipts = setOf(EventId("e1")),
                lastEventId = EventId("e24"),
                lastProcessedEventId = EventId("e23"),
                expectedMaxNotificationCount = 1,
            )
        )
    }

    @Test
    fun `with timeline - existing state - prefer server count`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e24")) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(
                StoredNotificationState.SyncWithTimeline(
                    roomId = roomId1,
                    needsSync = true,
                    readReceipts = setOf(EventId("e1")),
                    lastEventId = EventId("e24"),
                    lastProcessedEventId = EventId("e23"),
                    expectedMaxNotificationCount = 1,
                )
            ),
            notificationCounts = mapOf(roomId1 to 0)
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = false,
                readReceipts = setOf(EventId("e1")),
                lastEventId = EventId("e24"),
                lastProcessedEventId = EventId("e23"),
                expectedMaxNotificationCount = 0,
            )
        )
    }

    @Test
    fun `with timeline - new state`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e24")) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(),
            notificationCounts = mapOf(roomId1 to 1)
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = false,
                readReceipts = setOf(EventId("e1")),
                lastEventId = EventId("e24"),
                lastProcessedEventId = null,
                expectedMaxNotificationCount = 1,
            )
        )
    }


    @Test
    fun `with timeline - encrypted - ignore notification count`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = EventId("e24"), encrypted = true) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(),
            notificationCounts = mapOf(roomId1 to 1)
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithTimeline(
                roomId = roomId1,
                needsSync = false,
                readReceipts = setOf(EventId("e1")),
                lastEventId = EventId("e24"),
                lastProcessedEventId = null,
                expectedMaxNotificationCount = null,
            )
        )
    }

    @Test
    fun `no timeline - add to state`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = null) }
        processSyncWith(
            updatedRooms = setOf(roomId1),
            notificationStates = listOf(),
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf(
            StoredNotificationState.SyncWithoutTimeline(roomId = roomId1)
        )
    }

    @Test
    fun `push - remove`() = runTest {
        roomStore.update(roomId1) { simpleRoom.copy(roomId = roomId1, lastEventId = null) }
        processSyncWith(
            updatedRooms = setOf(),
            notificationStates = listOf(
                StoredNotificationState.Push(roomId1)
            ),
        )

        notificationStore.getAll().first().values.mapNotNull { it.first() } shouldBe listOf(
            notification1,
            notification2
        )
        notificationStore.getAllState().first().values.mapNotNull { it.first() } shouldBe listOf()
    }
}