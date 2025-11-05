package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.getInMemoryRoomUserStore
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomDisplayName
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.user.CanDoActionImpl
import net.folivo.trixnity.client.user.GetPowerLevelImpl
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class PushRuleConditionMatcherTest : TrixnityBaseTest() {

    private val roomId = RoomId("!room:localhost")
    private val userId = UserId("user1", "localhost")
    private val userInfo = UserInfo(userId, "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, ""))

    private val json: Json = createMatrixEventJson()
    private val roomStore = getInMemoryRoomStore { update(roomId) { Room(roomId) } }.apply {
        scheduleSetup {
            update(roomId) {
                simpleRoom.copy(
                    name = RoomDisplayName(
                        summary = Sync.Response.Rooms.JoinedRoom.RoomSummary(
                            joinedMemberCount = 1
                        )
                    )
                )
            }
        }
    }
    private val roomStateStore = getInMemoryRoomStateStore().apply {
        scheduleSetup {
            save(
                ClientEvent.RoomEvent.StateEvent(
                    content = CreateEventContent(roomVersion = "12"),
                    id = EventId("create"),
                    roomId = roomId,
                    sender = UserId("other", "server"),
                    originTimestamp = 1234,
                    stateKey = "",
                    unsigned = null,
                )
            )
        }
    }
    private val roomUserStore = getInMemoryRoomUserStore().apply {
        scheduleSetup {
            update(userId, roomId) {
                RoomUser(
                    roomId, userId, "Bob", ClientEvent.RoomEvent.StateEvent(
                        content = MemberEventContent(
                            displayName = "Bob",
                            membership = Membership.JOIN
                        ),
                        id = EventId("bob_member"),
                        roomId = roomId,
                        sender = userId,
                        originTimestamp = 1234,
                        stateKey = userId.full,
                        unsigned = null,
                    )
                )
            }
        }
    }

    private val canDoAction = CanDoActionImpl(userInfo, GetPowerLevelImpl())

    private fun messageEvent(content: MessageEventContent): ClientEvent<*> =
        ClientEvent.RoomEvent.MessageEvent(
            content = content,
            id = EventId("bla"),
            roomId = roomId,
            sender = userId,
            originTimestamp = 1234,
            unsigned = null,
        )

    private fun stateEvent(content: StateEventContent, stateKey: String = ""): ClientEvent<*> =
        ClientEvent.RoomEvent.StateEvent(
            content = content,
            id = EventId("bla"),
            roomId = roomId,
            sender = userId,
            originTimestamp = 1234,
            stateKey = stateKey,
            unsigned = null,
        )

    private suspend fun setUpNotificationPowerLevel(user: Long, levels: Map<String, Long>) =
        roomStateStore.save(
            ClientEvent.RoomEvent.StateEvent(
                content = PowerLevelsEventContent(
                    users = mapOf(userId to user),
                    notifications = levels
                ),
                id = EventId("power_level"),
                roomId = roomId,
                sender = userId,
                originTimestamp = 1234,
                stateKey = "",
                unsigned = null,
            )
        )

    @Test
    fun `ContainsDisplayName - no match`() = runTest {
        val event = messageEvent(Text("Hello Tina!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.ContainsDisplayName,
            event,
            userId,
            roomUserStore,
        ) shouldBe false
    }

    @Test
    fun `ContainsDisplayName - match`() = runTest {
        val event = messageEvent(Text("Hello Bob!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.ContainsDisplayName,
            event,
            userId,
            roomUserStore
        ) shouldBe true
    }

    @Test
    fun `RoomMemberCount - no match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.RoomMemberCount("2"),
            event,
            roomStore
        ) shouldBe false
    }

    @Test
    fun `RoomMemberCount - match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.RoomMemberCount("1"),
            event,
            roomStore
        ) shouldBe true
    }

    @Test
    fun `SenderNotificationPermission - no match`() = runTest {
        setUpNotificationPowerLevel(50, mapOf("dino" to 51L))
        val event = messageEvent(Text("Hello!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.SenderNotificationPermission("dino"),
            event,
            roomStateStore,
            canDoAction,
        ) shouldBe false
    }

    @Test
    fun `SenderNotificationPermission - match`() = runTest {
        setUpNotificationPowerLevel(50, mapOf("dino" to 50L))
        val event = messageEvent(Text("Hello!"))
        PushRuleConditionMatcherImpl.match(
            PushCondition.SenderNotificationPermission("dino"),
            event,
            roomStateStore,
            canDoAction
        ) shouldBe true
    }

    @Test
    fun `Eventno match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventMatch("content.body", "dino"),
            eventJson
        ) shouldBe false
    }

    @Test
    fun `Eventmatch`() = runTest {
        val event = messageEvent(Text("Hello!"))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventMatch("content.body", "*!"),
            eventJson
        ) shouldBe true
    }

    @Test
    fun `EventPropertyIs - no match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventPropertyIs("type", JsonPrimitive("m.room.mess")),
            eventJson
        ) shouldBe false
    }

    @Test
    fun `EventPropertyIs - match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventPropertyIs("type", JsonPrimitive("m.room.message")),
            eventJson
        ) shouldBe true
    }

    @Test
    fun `EventPropertyContains - no match`() = runTest {
        val event = stateEvent(CanonicalAliasEventContent(aliases = setOf(RoomAliasId("#unicorn"))))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventPropertyContains("content.alt_aliases", JsonPrimitive("#dino")),
            eventJson
        ) shouldBe false
    }

    @Test
    fun `EventPropertyContains - match`() = runTest {
        val event = stateEvent(CanonicalAliasEventContent(aliases = setOf(RoomAliasId("#dino"))))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl.match(
            PushCondition.EventPropertyContains("content.alt_aliases", JsonPrimitive("#dino")),
            eventJson
        ) shouldBe true
    }

    @Test
    fun `Unknown - no match`() = runTest {
        val event = messageEvent(Text("Hello!"))
        val eventJson = lazy { notificationEventToJson(event, json) }
        PushRuleConditionMatcherImpl(roomStore, roomStateStore, roomUserStore, canDoAction, userInfo)
            .match(
                PushCondition.Unknown(JsonObject(mapOf())),
                event,
                eventJson
            ) shouldBe false
    }
}