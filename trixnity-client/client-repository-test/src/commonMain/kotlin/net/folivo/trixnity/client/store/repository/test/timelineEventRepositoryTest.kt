package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.koin.core.Koin


fun ShouldSpec.timelineEventRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: TimelineEventRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("timelineEventRepositoryTest: save, get and delete") {
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("room1", "server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("room1", "server"))
        val event1 = TimelineEvent(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("room1", "server"),
                1234
            ),
            roomId = RoomId("room1", "server"),
            eventId = EventId("\$event1"),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        val event2 = TimelineEvent(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("room1", "server"),
                1234
            ),
            roomId = RoomId("room2", "server"),
            eventId = EventId("\$event2"),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        val session2Copy = event2.copy(nextEventId = EventId("\$superfancy"))

        rtm.writeTransaction {
            cut.save(key1, event1)
            cut.save(key2, event2)
            cut.get(key1) shouldBe event1
            cut.get(key2) shouldBe event2
            cut.save(key2, session2Copy)
            cut.get(key2) shouldBe session2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("timelineEventRepositoryTest: deleteByRoomId") {
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("room1", "server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("room2", "server"))
        val key3 = TimelineEventKey(EventId("\$event3"), RoomId("room1", "server"))
        val event1 = TimelineEvent(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("room1", "server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        val event2 = TimelineEvent(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("room2", "server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        val event3 = TimelineEvent(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("room1", "server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )

        rtm.writeTransaction {
            cut.save(key1, event1)
            cut.save(key2, event2)
            cut.save(key3, event3)

            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(key1) shouldBe null
            cut.get(key2) shouldBe event2
            cut.get(key3) shouldBe null
        }
    }
}