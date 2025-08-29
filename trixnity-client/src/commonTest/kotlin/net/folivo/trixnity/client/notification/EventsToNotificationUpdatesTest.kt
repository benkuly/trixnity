package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.notification.NotificationUpdate.Change
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased.Text
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.scheduleSetup
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
        eventContentSerializerMappings = DefaultEventContentSerializerMappings,
        userInfo = UserInfo(ownUserId, "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, ""))
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
    fun `process all updates`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someStateEvent,
                someMessageEvent
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.New(setOf(PushAction.Notify)),
            ),
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.New(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `state event - already processed - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someStateEvent,
                someStateEvent.copy(id = EventId("\$event3"))
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.New(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `state event - no match - remove notification`() = runTest {
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            flowOf(
                someStateEvent,
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.Remove,
            ),
        )
    }

    @Test
    fun `state event - match - new notification`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someStateEvent,
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.New(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `message event - already processed - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent,
                someMessageEvent
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.New(setOf(PushAction.Notify)),
            )
        )
    }

    @Test
    fun `message event - no match - ignore`() = runTest {
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            flowOf(
                someMessageEvent,
            ),
            listOf()
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - match - new notification`() = runTest {
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent,
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.New(setOf(PushAction.Notify)),
            )
        )
    }

    @Test
    fun `message event - redaction of state - already processed - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), null))
        cut.invoke(
            flowOf(
                someStateEvent,
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.New(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `message event - redaction of state - no state known - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            listOf()
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - redaction of state - update with old state`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someStateEvent))
        roomServiceMock.state.value =
            mapOf(RoomServiceMock.GetStateKey(roomId, someStateEvent.content::class, userId.full) to someStateEvent)
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someStateEvent.id)),
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.New(setOf(PushAction.Notify)),
            ),
            NotificationUpdate.State(
                roomId = roomId,
                eventId = someStateEvent.id,
                type = "m.room.member",
                stateKey = userId.full,
                change = Change.New(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `message event - redact message - match`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent2))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent.copy(content = RedactionEventContent(redacts = someMessageEvent2.id)),
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.New(setOf(PushAction.Notify)),
            ),
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent2.id,
                change = Change.Remove,
            ),
        )
    }

    @Test
    fun `message event - replace message - already processed - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify), null))
        cut.invoke(
            flowOf(
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
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.Update(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `message event - replace message - no event found - ignore`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(null)
        evaluatePushRules.result.addAll(listOf(null))
        cut.invoke(
            flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                ),
            ),
            listOf()
        ).toList() shouldBe listOf()
    }

    @Test
    fun `message event - replace message - match - update notification`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(null, setOf(PushAction.Notify)))
        cut.invoke(
            flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                )
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.Update(setOf(PushAction.Notify)),
            ),
        )
    }

    @Test
    fun `message event - replace message - no match - remove notification`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf(TimelineEvent(someMessageEvent))
        evaluatePushRules.result.addAll(listOf(setOf(PushAction.Notify), null))
        cut.invoke(
            flowOf(
                someMessageEvent2.copy(
                    content = Text(
                        body = "hi 3!",
                        relatesTo = RelatesTo.Replace(someMessageEvent.id)
                    )
                )
            ),
            listOf()
        ).toList() shouldBe listOf(
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent2.id,
                change = Change.New(setOf(PushAction.Notify)),
            ),
            NotificationUpdate.Message(
                roomId = roomId,
                eventId = someMessageEvent.id,
                change = Change.Remove,
            ),
        )
    }
}