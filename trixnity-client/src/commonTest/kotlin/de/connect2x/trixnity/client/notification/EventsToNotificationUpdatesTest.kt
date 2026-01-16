package de.connect2x.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.client.ClockMock
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.StoredNotificationUpdate.Content
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRule
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import kotlin.test.Test

class EventsToNotificationUpdatesTest : TrixnityBaseTest() {
    private val roomId = RoomId("!room")
    private val ownUserId = UserId("own", "server")
    private val userId = UserId("user", "server")
    private val roomServiceMock = RoomServiceMock().apply {
        scheduleSetup {
            returnGetTimelineEvent = flowOf(null)
            state.value = mapOf()
        }
    }

    private val clock = ClockMock()

    private class MockEvaluatePushRules() : EvaluatePushRules {
        val result: MutableList<Set<PushAction>?> = mutableListOf()
        override suspend fun invoke(event: ClientEvent<*>, allRules: List<PushRule>): Set<PushAction>? =
            result.removeFirst()
    }

    private val evaluatePushRules = MockEvaluatePushRules().apply {
        scheduleSetup { result.clear() }
    }

    private val cut = EventsToNotificationUpdatesImpl(
        roomService = roomServiceMock,
        evaluatePushRules = evaluatePushRules,
        eventContentSerializerMappings = EventContentSerializerMappings.default,
        userInfo = UserInfo(
            ownUserId,
            "device",
            Key.Ed25519Key(null, "!room-1970-01-01T06:44:02.424Z-ffffffff"),
            Key.Curve25519Key(null, "!room-1970-01-01T06:44:02.424Z-ffffffff")
        ),
        clock = clock,

        )

    private val someMessageEvent = ClientEvent.RoomEvent.MessageEvent<MessageEventContent>(
        content = Text("hi!"),
        id = EventId("\$event1"),
        roomId = roomId,
        sender = userId,
        originTimestamp = 1234,
        unsigned = null,
    )

    private val someMessageEvent2 = ClientEvent.RoomEvent.MessageEvent<MessageEventContent>(
        content = Text("hi 2!"),
        id = EventId("\$event2"),
        roomId = roomId,
        sender = userId,
        originTimestamp = 1234,
        unsigned = null,
    )
    private val someStateEvent = ClientEvent.RoomEvent.StateEvent<StateEventContent>(
        content = MemberEventContent(membership = Membership.JOIN),
        id = EventId("\$event3"),
        roomId = roomId,
        sender = userId,
        originTimestamp = 1234,
        stateKey = userId.full,
        unsigned = null,
    )

    @Test
    fun `process all updates and sort via key`() = runTest {
        evaluatePushRules.result.addAll(
            listOf(
                setOf(PushAction.Notify),
                setOf(PushAction.Notify),
                setOf(PushAction.Notify)
            )
        )
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
                someMessageEvent,
                someMessageEvent2,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-fffffffe",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent2.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-fffffffd",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent2.id),
            ),
        )
    }

    @Test
    fun `remove stale notifications`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.Message.id(roomId, someMessageEvent.id) to "s1",
                StoredNotification.State.id(roomId, "m.room.member", userId.full) to "s2",
            ),
            removeStale = true,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "s2",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                roomId = roomId,
            ),
        )
    }

    @Test
    fun `do not remove stale notifications`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.Message.id(roomId, someMessageEvent.id) to "s1",
                StoredNotification.State.id(roomId, "m.room.member", userId.full) to "s2",
            ),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.Update(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "s2",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
        )
    }

    @Test
    fun `state event - already processed - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
                someStateEvent.copy(id = EventId("\$event3"))
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
        )
    }

    @Test
    fun `state event - no match - notification exists - remove`() = runTest {
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.State.id(roomId, "m.room.member", userId.full) to "s"
            ),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.Remove(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                roomId = roomId,
            ),
        )
    }

    @Test
    fun `state event - no match - notification does not exist - do nothing`() = runTest {
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf()
    }

    @Test
    fun `state event - match - new notification`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
        )
    }

    @Test
    fun `message event - already processed - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent,
                someMessageEvent
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
        )
    }

    @Test
    fun `message event - no match - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - match - new notification`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent,
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
        )
    }

    @Test
    fun `message event - redaction of state - already processed - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someStateEvent,
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
        )
    }

    @Test
    fun `message event - redaction of state - no state known - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - redaction of state - update with old state`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        roomServiceMock.state.value =
            mapOf(RoomServiceMock.GetStateKey(roomId, someStateEvent.content::class, userId.full) to someStateEvent)
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
            StoredNotificationUpdate.New(
                id = StoredNotification.State.id(roomId, "m.room.member", userId.full),
                sortKey = "!room-1970-01-01T06:44:02.424Z-fffffffe",
                actions = setOf(PushAction.Notify),
                content = Content.State(roomId, someStateEvent.id, "m.room.member", userId.full),
            ),
        )
    }

    @Test
    fun `message event - redact message - match`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent2))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someMessageEvent2.id)),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId, someMessageEvent2.id),
                roomId = roomId,
            ),
        )
    }

    @Test
    fun `message event - replace message - already processed - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify), null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                ),
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 4!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                ),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.Message.id(roomId, someMessageEvent.id) to "s"
            ),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "s",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
        )
    }

    @Test
    fun `message event - replace message - no event found - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(null)
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                ),
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(),
            removeStale = false,
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - replace message - match - update notification`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify)))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                )
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.Message.id(roomId, someMessageEvent.id) to "s"
            ),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.Update(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                sortKey = "s",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent.id),
            ),
        )
    }

    @Test
    fun `message event - replace message - no match - remove notification`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), null))
        cut.invoke(
            roomId = roomId,
            eventFlow = flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                )
            ),
            pushRules = listOf(),
            existingNotifications = mapOf(
                StoredNotification.Message.id(roomId, someMessageEvent.id) to "s"
            ),
            removeStale = false,
        ).toList() shouldBe listOf(
            StoredNotificationUpdate.New(
                id = StoredNotification.Message.id(roomId, someMessageEvent2.id),
                sortKey = "!room-1970-01-01T06:44:02.424Z-ffffffff",
                actions = setOf(PushAction.Notify),
                content = Content.Message(roomId, someMessageEvent2.id),
            ),
            StoredNotificationUpdate.Remove(
                id = StoredNotification.Message.id(roomId, someMessageEvent.id),
                roomId = roomId,
            ),
        )
    }
}