package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTimelineEventRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomTimelineEventRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomTimelineEventRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
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

        repo.save(key1, event1)
        repo.save(key2, event2)
        repo.get(key1) shouldBe event1
        repo.get(key2) shouldBe event2
        repo.save(key2, session2Copy)
        repo.get(key2) shouldBe session2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }

    @Test
    fun deleteByRoomId() = runTest {
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

        repo.save(key1, event1)
        repo.save(key2, event2)
        repo.save(key3, event3)

        repo.deleteByRoomId(RoomId("room1", "server"))
        repo.get(key1) shouldBe null
        repo.get(key2) shouldBe event2
        repo.get(key3) shouldBe null
    }
}
