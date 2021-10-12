package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightRoomTimelineRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightRoomTimelineRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightRoomTimelineRepository(
            Database(driver).roomTimelineQueries,
            createMatrixJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = RoomTimelineKey(MatrixId.EventId("\$event1"), RoomId("room1", "server"))
        val key2 = RoomTimelineKey(MatrixId.EventId("\$event2"), RoomId("room1", "server"))
        val event1 = TimelineEvent(
            Event.MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                MatrixId.EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("room1", "server"),
                1234
            ),
            roomId = RoomId("room1", "server"),
            eventId = MatrixId.EventId("\$event1"),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        val event2 = TimelineEvent(
            Event.MessageEvent(
                RoomMessageEventContent.TextMessageEventContent("message"),
                MatrixId.EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("room2", "server"),
                1234
            ),
            roomId = RoomId("room2", "server"),
            eventId = MatrixId.EventId("\$event2"),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        val session2Copy = event2.copy(nextEventId = MatrixId.EventId("\$superfancy"))

        cut.save(key1, event1)
        cut.save(key2, event2)
        cut.get(key1) shouldBe event1
        cut.get(key2) shouldBe event2
        cut.save(key2, session2Copy)
        cut.get(key2) shouldBe session2Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})