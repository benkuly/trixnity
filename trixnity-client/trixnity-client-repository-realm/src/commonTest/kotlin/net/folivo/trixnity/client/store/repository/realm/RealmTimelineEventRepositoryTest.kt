package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson


class RealmTimelineEventRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmTimelineEventRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmTimelineEvent::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmTimelineEventRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("room1", "server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("room1", "server"))
        val event1 = TimelineEvent(
            Event.MessageEvent(
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
            Event.MessageEvent(
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

        writeTransaction(realm) {
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
    should("deleteByRoomId") {
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("room1", "server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("room2", "server"))
        val key3 = TimelineEventKey(EventId("\$event3"), RoomId("room1", "server"))
        val event1 = TimelineEvent(
            Event.MessageEvent(
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
            Event.MessageEvent(
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
            Event.MessageEvent(
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

        writeTransaction(realm) {
            cut.save(key1, event1)
            cut.save(key2, event2)
            cut.save(key3, event3)

            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(key1) shouldBe null
            cut.get(key2) shouldBe event2
            cut.get(key3) shouldBe null
        }
    }
})