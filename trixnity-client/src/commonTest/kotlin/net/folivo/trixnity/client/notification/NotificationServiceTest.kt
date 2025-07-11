package net.folivo.trixnity.client.notification

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.notification.NotificationService.Notification
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.PushRulesEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.push.PushAction.Notify
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleSet
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NotificationServiceTest : TrixnityBaseTest() {

    private val roomStore = getInMemoryRoomStore { update(roomId) { Room(roomId) } }
    private val roomStateStore = getInMemoryRoomStateStore()
    private val roomUserStore = getInMemoryRoomUserStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val json = createMatrixEventJson()
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig, json)

    private val room = RoomServiceMock()

    private val roomId = RoomId("!room:localhost")
    private val user1 = UserId("user1", "localhost")
    private val otherUser = UserId("otherUser", "localhost")
    private val user1DisplayName = "User1 🦊"
    private val currentSyncState = MutableStateFlow(SyncState.RUNNING)

    private val cut = NotificationServiceImpl(
        UserInfo(user1, "", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api,
        room,
        roomStore,
        roomStateStore,
        roomUserStore,
        globalAccountDataStore,
        json,
        CurrentSyncState(currentSyncState)
    )

    @Test
    fun `getNotifications » wait for sync to be started or running`() = runTest {
        currentSyncState.value = SyncState.INITIAL_SYNC
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(
                    listOf(
                        pushRuleInvitation(),
                    )
                )
            )
        )
        val invitation = StrippedStateEvent(
            content = MemberEventContent(
                membership = Membership.INVITE,
                displayName = user1DisplayName,
            ),
            sender = otherUser,
            roomId = roomId,
            stateKey = user1.full,
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response(
                    nextBatch = "next",
                    room = Sync.Response.Rooms(
                        invite = mapOf(
                            roomId to Sync.Response.Rooms.InvitedRoom(
                                inviteState = Sync.Response.Rooms.InvitedRoom.InviteState(
                                    events = listOf(invitation)
                                )
                            )
                        )
                    )
                )
            }
        }

        checkNoNotification()
    }

    @Test
    fun `getNotifications » do nothing when no events`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response("next")
            }
        }
        globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

        checkNoNotification()
    }

    @Test
    fun `getNotifications » notify on invite`() = runTest {
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(
                    listOf(
                        pushRuleInvitation(),
                    )
                )
            )
        )
        val invitation = StrippedStateEvent(
            content = MemberEventContent(
                membership = Membership.INVITE,
                displayName = user1DisplayName,
            ),
            sender = otherUser,
            roomId = roomId,
            stateKey = user1.full,
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response(
                    nextBatch = "next",
                    room = Sync.Response.Rooms(
                        invite = mapOf(
                            roomId to Sync.Response.Rooms.InvitedRoom(
                                inviteState = Sync.Response.Rooms.InvitedRoom.InviteState(
                                    events = listOf(invitation)
                                )
                            )
                        )
                    )
                )
            }
        }

        checkNotifications {
            it.first() shouldBe Notification(invitation, setOf(Notify))
        }
    }

    private val newTimelineEvent = messageEventWithContent(
        roomId, RoomMessageEventContent.TextBased.Text(
            body = "Hello User1 🦊!"
        )
    )

    @Test
    fun `getNotification » new timeline events » check push rules and notify`() = runTest {
        newTimelineEventsSetup()
        checkNotifications {
            it.first() shouldBe Notification(newTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotification » new timeline events » have correct order`() = runTest {
        newTimelineEventsSetup()
        val timelineEvents = (0..99).map {
            messageEventWithContent(
                roomId, RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊! ($it)"
                )
            )
        }
        room.returnGetTimelineEventsOnce = timelineEvents.asFlow()

        checkNotifications { notifications ->
            notifications.take(100).toList() shouldBe timelineEvents.map { Notification(it.event, setOf(Notify)) }
        }
    }

    @Test
    fun `getNotification » new timeline events » not notify on own messages`() = runTest {
        newTimelineEventsSetup()
        val timelineEvents = (0..9).map {
            messageEventWithContent(
                roomId, RoomMessageEventContent.TextBased.Text(
                    body = "Hello User1 🦊! ($it)"
                ),
                sender = if (it == 0 || it == 9) user1 else otherUser
            )
        }
        room.returnGetTimelineEventsOnce = timelineEvents.asFlow()

        checkNotifications { notifications ->
            notifications.take(8).toList() shouldBe timelineEvents.drop(1).dropLast(1).map {
                Notification(it.event, setOf(Notify))
            }
        }
    }

    private val newDecryptedTimelineEvent = messageEventWithContent(
        roomId, MegolmEncryptedMessageEventContent(
            "", Curve25519KeyValue(""), "", ""
        ), RoomMessageEventContent.TextBased.Text(
            body = "Hello User1 🦊!"
        )
    )

    @Test
    fun `getTimelineEvents » new decrypted timeline events » check push rules and notify`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response("next")
            }
        }
        room.returnGetTimelineEventsOnce = flowOf(newDecryptedTimelineEvent)

        setUser1DisplayName(roomId)
        globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))

        checkNotifications {
            assertSoftly(it.first()) {
                event.idOrNull shouldBe newDecryptedTimelineEvent.eventId
                event.content shouldBe newDecryptedTimelineEvent.content?.getOrThrow()
            }
        }
    }

    private val pushRulesTimelineEvent = messageEventWithContent(
        roomId, RoomMessageEventContent.TextBased.Text(
            body = "Hello User1 🦊!"
        )
    )

    @Test
    fun `getNotifications » push rules » room member count » notify when met`() = runTest {
        roomMemberCountSetup()
        roomStore.update(roomId) {
            Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 2)))
        }

        checkNotifications {
            it.first() shouldBe Notification(pushRulesTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotifications » push rules » room member count » not notify when not met`() = runTest {
        roomMemberCountSetup()
        roomStore.update(roomId) {
            Room(roomId, name = RoomDisplayName(summary = RoomSummary(joinedMemberCount = 1)))
        }
        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » permission level » notify when met`() = runTest {
        permissionLevelSetup()
        roomStateStore.save(
            StateEvent(
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

        checkNotifications {
            it.first() shouldBe Notification(pushRulesTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotifications » push rules » permission level » not notify when not met`() = runTest {
        permissionLevelSetup()
        roomStateStore.save(
            StateEvent(
                PowerLevelsEventContent(
                    notifications = PowerLevelsEventContent.Notifications(100),
                    users = mapOf(otherUser to 50, user1 to 30)
                ),
                id = EventId("\$powerLevel"),
                sender = user1,
                roomId = roomId,
                originTimestamp = 0L,
                stateKey = "",
            )
        )
        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » not notify when push rule disabled`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(listOf(pushRuleEventMatchTriggeredNotEnabled()))
            )
        )
        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » multiple conditions » should notify when all conditions match`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(listOf(pushRuleWithMultipleConditions()))
            )
        )
        setUser1DisplayName(roomId)

        checkNotifications {
            it.first() shouldBe Notification(pushRulesTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotifications » push rules » multiple conditions » not notify when one condition matches`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(listOf(pushRuleWithMultipleConditions()))
            )
        )
        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » always notify when no conditions`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                pushRules(listOf(pushRuleNoCondition()))
            )
        )
        checkNotifications {
            it.first() shouldBe Notification(pushRulesTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotifications » push rules » override`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                PushRulesEventContent(
                    global = PushRuleSet(
                        override = listOf(
                            PushRule.Override(
                                ruleId = "customRule10",
                                enabled = true,
                                default = false,
                                conditions = setOf(PushCondition.EventMatch("content.body", "*User*")),
                                actions = setOf()
                            ),
                            pushRuleDisplayName()
                        )
                    )
                )
            )
        )
        setUser1DisplayName(roomId)

        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » room push rules » ignore other room's rules`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                PushRulesEventContent(
                    global = PushRuleSet(
                        override = listOf(
                            pushRuleDisplayName()
                        ),
                        room = listOf(
                            PushRule.Room(
                                roomId = RoomId("!andNowForSomethingCompletelyDifferent:localhost"),
                                enabled = true,
                                default = false,
                                actions = setOf(Notify)
                            )
                        )
                    )
                )
            )
        )

        checkNoNotification()
    }

    @Test
    fun `getNotifications » push rules » room push rules » consider this room's rule`() = runTest {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(
                PushRulesEventContent(
                    global = PushRuleSet(
                        override = listOf(
                            pushRuleDisplayName()
                        ),
                        room = listOf(
                            PushRule.Room(
                                roomId = roomId,
                                enabled = true,
                                default = false,
                                actions = setOf(Notify)
                            )
                        )
                    )
                )
            )
        )

        checkNotifications {
            it.first() shouldBe Notification(pushRulesTimelineEvent.event, setOf(Notify))
        }
    }

    @Test
    fun `getNotifications » push actions » not notify when action says it`() = runTest {
        val timelineEvent = messageEventWithContent(
            roomId, RoomMessageEventContent.TextBased.Text(
                body = "Hello User1 🦊!"
            )
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response("next")
            }
        }
        room.returnGetTimelineEventsOnce = flowOf(timelineEvent)
        globalAccountDataStore.save(
            GlobalAccountDataEvent(pushRules(listOf(pushRuleEventMatchTriggeredDontNotify())))
        )
        checkNoNotification()
    }


    @Test
    fun `getEventProperty » get property from path`() = runTest {
        val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b" to JsonPrimitive("value"))))) }
        cut.getEventProperty(input, "a.b") shouldBe JsonPrimitive("value")
    }

    @Test
    fun `getEventProperty » return null when property not found`() = runTest {
        val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))) }
        cut.getEventProperty(input, "a.b.c") shouldBe null
    }

    @Test
    fun `getEventProperty » escape path`() = runTest {
        val input = lazy { JsonObject(mapOf("a" to JsonObject(mapOf("b.c" to JsonPrimitive("value"))))) }
        cut.getEventProperty(input, "a.b\\.c") shouldBe JsonPrimitive("value")
    }

    private suspend fun newTimelineEventsSetup() {
        setUser1DisplayName(roomId)
        globalAccountDataStore.save(GlobalAccountDataEvent(pushRules(listOf(pushRuleDisplayName()))))
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response("next")
            }
        }
        room.returnGetTimelineEventsOnce = flowOf(newTimelineEvent)
    }

    private fun pushRulesSetup() {
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync(timeout = 0)) {
                Sync.Response("next")
            }
        }
        room.returnGetTimelineEventsOnce = flowOf(pushRulesTimelineEvent)
    }

    private suspend fun roomMemberCountSetup() {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(pushRules(listOf(pushRuleMemberCountGreaterEqual2())))
        )
    }

    private suspend fun permissionLevelSetup() {
        pushRulesSetup()
        globalAccountDataStore.save(
            GlobalAccountDataEvent(pushRules(listOf(pushRulePowerLevelRoom())))
        )
    }

    private fun pushRules(overridePushRules: List<PushRule.Override>) = PushRulesEventContent(
        global = PushRuleSet(
            override = overridePushRules,
            content = listOf(),
            room = listOf(),
            sender = listOf(),
            underride = listOf(),
        )
    )

    private fun pushRuleDisplayName() = PushRule.Override(
        ruleId = ".m.rule.contains_display_name",
        enabled = true,
        default = true,
        conditions = setOf(PushCondition.ContainsDisplayName),
        actions = setOf(Notify),
    )

    private fun pushRuleInvitation() = PushRule.Override(
        ruleId = ".m.rule.invite_for_me",
        enabled = true,
        default = true,
        conditions = setOf(
            PushCondition.EventMatch(key = "type", pattern = "m.room.member"),
            PushCondition.EventMatch(key = "content.membership", "invite"),
            PushCondition.EventMatch(key = "state_key", pattern = user1.full),
        ),
        actions = setOf(Notify),
    )

    private fun pushRuleEventMatchTriggeredDontNotify() = PushRule.Override(
        ruleId = "customRule1",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(),
    )

    private fun pushRuleEventMatchTriggeredNotEnabled() = PushRule.Override(
        ruleId = "customRule1",
        enabled = false,
        default = false,
        conditions = setOf(PushCondition.EventMatch(key = "content.body", "*User*")),
        actions = setOf(Notify),
    )

    private fun pushRuleMemberCountGreaterEqual2() = PushRule.Override(
        ruleId = "customRule2",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.RoomMemberCount(">=2")),
        actions = setOf(Notify)
    )

    private fun pushRulePowerLevelRoom() = PushRule.Override(
        ruleId = "customRule3",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.SenderNotificationPermission("room")),
        actions = setOf(Notify)
    )

    private fun pushRuleNoCondition() = PushRule.Override(
        ruleId = "customRule4",
        enabled = true,
        default = false,
        conditions = setOf(),
        actions = setOf(Notify)
    )

    private fun pushRuleWithMultipleConditions() = PushRule.Override(
        ruleId = "customRule5",
        enabled = true,
        default = false,
        conditions = setOf(PushCondition.ContainsDisplayName, PushCondition.EventMatch("content.body", "*User*")),
        actions = setOf(Notify)
    )

    private fun messageEventWithContent(
        roomId: RoomId,
        content: MessageEventContent,
        decryptedContent: MessageEventContent = content,
        sender: UserId = otherUser
    ) = TimelineEvent(
        event = MessageEvent(
            content = content,
            id = EventId("\$event-${content.hashCode()}"),
            sender = sender,
            roomId = roomId,
            originTimestamp = 0L,
        ),
        content = Result.success(decryptedContent),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )

    private suspend fun setUser1DisplayName(roomId: RoomId) {
        roomUserStore.update(
            user1,
            roomId
        ) {
            RoomUser(
                roomId,
                user1,
                user1DisplayName,
                StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("JOIN"),
                    user1,
                    roomId,
                    0,
                    stateKey = ""
                )
            )
        }
    }

    private suspend fun checkNoNotification() = coroutineScope {
        api.sync.start()

        val notifications = async { cut.getNotifications(0.seconds).first() }

        continually(50.milliseconds) {
            notifications.isCompleted shouldBe false
        }
        notifications.cancel()

        api.sync.cancel()
    }

    private suspend fun checkNotifications(block: suspend (Flow<Notification>) -> Unit) = coroutineScope {
        api.sync.start()

        block(cut.getNotifications(0.seconds))

        api.sync.cancel()
    }
}
