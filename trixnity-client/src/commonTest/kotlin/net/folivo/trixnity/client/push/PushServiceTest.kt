package net.folivo.trixnity.client.push

import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixHttpClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponse.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponseSerializer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushAction.DontNotify
import net.folivo.trixnity.core.model.push.PushAction.Notify
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleSet
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PushServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val json = createMatrixJson()
    val api: MatrixClientServerApiClient = mockk(relaxed = true)
    val room: RoomService = mockk()

    private val user1 = UserId("user1", "localhost")
    private val otherUser = UserId("otherUser", "localhost")

    @BeforeTest
    fun before() {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope)
    }

    @AfterTest
    fun after() {
        clearAllMocks()
        storeScope.cancel()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun whenNoEventsAreInTimelineShouldDoNothing() = runTest(context = testDispatcher, dispatchTimeoutMs = 3_000) {
        store.init()
        store.globalAccountData.update(
            Event.GlobalAccountDataEvent(
                pushRules(listOf(pushRuleDisplayName()))
            )
        )

        val roomId = RoomId("room", "localhost")
        val syncApiClient = syncApiClientWithResponse(
            SyncResponse.Rooms(
                join = mapOf(
                    roomId to SyncResponse.Rooms.JoinedRoom(
                        timeline = SyncResponse.Rooms.Timeline(
                            events = listOf()
                        )
                    )
                )
            )
        )
        coEvery { api.sync } returns syncApiClient

        val cut = PushService(api, room, store, json)
        val scope = CoroutineScope(testDispatcher)
        val notifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, 2)
        cut.start(scope)
        syncApiClient.startOnce { }.getOrThrow()

        advanceUntilIdle()
        notifications.replayCache should beEmpty()
    }

    @Test
    fun whenOneInterestingEventInTimelineThenShouldCheckPushRules() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(listOf(pushRuleDisplayName()))
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent(
                    body = "Hello @user1!"
                )
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val notifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            notifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, messageEvent.content)
            )
        }

    @Test
    fun whenThereIsOneInterestingEncryptedEventInTheTimelineThenShouldCheckPushRules() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(listOf(pushRuleDisplayName()))
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = EncryptionAlgorithm.Megolm) }

            val messageEvent = messageEventWithContent(
                roomId, EncryptedEventContent.MegolmEncryptedEventContent(
                    ciphertext = "123abc456",
                    senderKey = Key.Curve25519Key(value = ""),
                    deviceId = "",
                    sessionId = ""
                )
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val megolmEvent = Event.MegolmEvent(TextMessageEventContent(body = "Hello @user1!"), roomId)
            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
                decryptedEvent = Result.success(
                    megolmEvent
                )
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val notifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            notifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, megolmEvent.content)
            )
        }

    @Test
    fun multipleInterestingTimelineEventsShouldBeCheckedForPushRules() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleDisplayName(),
                            pushRuleEventMatchTriggered(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent1 = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val messageEvent2 = messageEventWithContent(
                roomId, TextMessageEventContent("I am triggered.")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent1, messageEvent2)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent1 = TimelineEvent(
                event = messageEvent1,
                roomId = roomId,
                eventId = messageEvent1.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            val timelineEvent2 = TimelineEvent(
                event = messageEvent2,
                roomId = roomId,
                eventId = messageEvent2.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent1.id, roomId, any()) } returns MutableStateFlow(timelineEvent1)
            coEvery { room.getTimelineEvent(messageEvent2.id, roomId, any()) } returns MutableStateFlow(timelineEvent2)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent1, messageEvent1.content),
                PushService.Notification(messageEvent2, messageEvent2.content)
            )
        }

    @Test
    fun whenRoomMemberCountIsMetShouldSetNotification() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(pushRuleMemberCountGreaterEqual2())
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) {
                Room(
                    roomId,
                    encryptionAlgorithm = null,
                    name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 2))
                )
            }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, messageEvent.content)
            )
        }

    @Test
    fun whenPermissionLevelIsMetShouldSetNotification() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(pushRulePowerLevelRoom())
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) {
                Room(roomId, encryptionAlgorithm = null)
            }
            store.roomState.update(
                Event.StateEvent(
                    PowerLevelsEventContent(
                        notifications = PowerLevelsEventContent.Notifications(50),
                        users = mapOf(otherUser to 50, user1 to 30)
                    ),
                    id = EventId("\$powerLevel"),
                    sender = user1,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, messageEvent.content)
            )
        }

    @Test
    fun whenPushRuleConditionIsNotMetShouldNotSetNotification() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(pushRulePowerLevelRoom())
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) {
                Room(roomId, encryptionAlgorithm = null)
            }
            store.roomState.update(
                Event.StateEvent(
                    PowerLevelsEventContent(
                        notifications = PowerLevelsEventContent.Notifications(50),
                        users = mapOf(otherUser to 30, user1 to 30)
                    ),
                    id = EventId("\$powerLevel"),
                    sender = user1,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache should beEmpty()
        }

    @Test
    fun whenActionOfPushRuleDoesNotSayNotifyShouldNotSetNotification() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleEventMatchTriggeredDontNotify(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("I am triggered.")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache should beEmpty()
        }

    @Test
    fun whenPushRuleIsNotEnableButWouldBeSatisfiedShouldNotSetNotification() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleEventMatchTriggeredNotEnabled(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("I am triggered.")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache should beEmpty()
        }

    @Test
    fun allConditionsOfAPushRuleShouldMatchToNotify() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleWithMultipleConditions(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1! I am triggered.")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, messageEvent.content)
            )
        }

    @Test
    fun aRuleWithNoConditionsAlwaysMatches() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    pushRules(
                        listOf(
                            pushRuleNoCondition(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache shouldContainExactly listOf(
                PushService.Notification(messageEvent, messageEvent.content)
            )
        }

    @Test
    fun overrideShouldBeOfHigherPriorityThanOtherRules() =
        runTest(context = testDispatcher, dispatchTimeoutMs = 2_000) {
            val roomId = RoomId("room", "localhost")
            store.init()
            store.globalAccountData.update(
                Event.GlobalAccountDataEvent(
                    PushRulesEventContent(
                        global = PushRuleSet(
                            override = listOf(
                                PushRule(
                                    ruleId = "customRule10",
                                    enabled = true,
                                    default = false,
                                    conditions = setOf(PushCondition.EventMatch("content.body", "*user*")),
                                    actions = setOf(DontNotify)
                                )
                            ),
                            content = listOf(
                                pushRuleDisplayName()
                            ),
                            room = listOf(),
                            sender = listOf(),
                            underride = listOf(),
                        )
                    )
                )
            )
            store.account.userId.value = user1
            store.room.update(roomId) { Room(roomId, encryptionAlgorithm = null) }

            val messageEvent = messageEventWithContent(
                roomId, TextMessageEventContent("Hello @user1!")
            )
            val syncApiClient = syncApiClientWithResponse(
                SyncResponse.Rooms(
                    join = mapOf(
                        roomId to SyncResponse.Rooms.JoinedRoom(
                            timeline = SyncResponse.Rooms.Timeline(
                                events = listOf(messageEvent)
                            )
                        )
                    )
                ),
            )
            coEvery { api.sync } returns syncApiClient

            val timelineEvent = TimelineEvent(
                event = messageEvent,
                roomId = roomId,
                eventId = messageEvent.id,
                previousEventId = null,
                nextEventId = null,
                gap = null,
            )
            coEvery { room.getTimelineEvent(messageEvent.id, roomId, any()) } returns MutableStateFlow(timelineEvent)

            val cut = PushService(api, room, store, json)
            val scope = CoroutineScope(testDispatcher)
            val allNotifications = cut.notifications.shareIn(scope, SharingStarted.Eagerly, replay = 2)
            cut.start(scope)
            syncApiClient.startOnce { }.getOrThrow()

            advanceUntilIdle()
            allNotifications.replayCache should beEmpty()
        }

    private fun pushRules(contentPushRules: List<PushRule>) = PushRulesEventContent(
        global = PushRuleSet(
            content = contentPushRules,
            override = listOf(),
            room = listOf(),
            sender = listOf(),
            underride = listOf(),
        )
    )

    private fun pushRuleDisplayName() = PushRule(
        ruleId = ".m.rule.contains_display_name",
        enabled = true,
        default = true,
        conditions = setOf(PushCondition.ContainsDisplayName),
        actions = setOf(Notify),
    )

    private fun pushRuleEventMatchTriggered() = PushRule(
        ruleId = "customRule1",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*triggered*")),
        actions = setOf(Notify),
    )

    private fun pushRuleEventMatchTriggeredDontNotify() = PushRule(
        ruleId = "customRule1",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*triggered*")),
        actions = setOf(DontNotify),
    )

    private fun pushRuleEventMatchTriggeredNotEnabled() = PushRule(
        ruleId = "customRule1",
        enabled = false,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*triggered*")),
        actions = setOf(Notify),
    )

    private fun pushRuleMemberCountGreaterEqual2() = PushRule(
        ruleId = "customRule2",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.RoomMemberCount(">=2")),
        actions = setOf(Notify)
    )

    private fun pushRulePowerLevelRoom() = PushRule(
        ruleId = "customRule3",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.SenderNotificationPermission("room")),
        actions = setOf(Notify)
    )

    private fun pushRuleNoCondition() = PushRule(
        ruleId = "customRule4",
        enabled = true,
        default = false,
        conditions = setOf(),
        actions = setOf(Notify)
    )

    private fun pushRuleWithMultipleConditions() = PushRule(
        ruleId = "customRule5",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.EventMatch("content.body", "*triggered*")),
        actions = setOf(Notify)
    )

    private fun syncApiClientWithResponse(rooms: SyncResponse.Rooms) = SyncApiClient(
        MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { _ ->
                        respond(
                            json.encodeToString(
                                SyncResponseSerializer, SyncResponse(
                                    nextBatch = "next",
                                    room = rooms,
                                )
                            ),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )
    )

    private fun messageEventWithContent(
        roomId: RoomId, content: MessageEventContent
    ): MessageEvent<*> = MessageEvent(
        content = content,
        id = EventId("\$event-${content.hashCode()}"),
        sender = otherUser,
        roomId = roomId,
        originTimestamp = 0L,
    )
}