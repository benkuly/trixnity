package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriver
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings

class SqlDelightStoreTest : ShouldSpec({
    lateinit var driver: SqlDriver
    lateinit var cut: SqlDelightStore
    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        driver = createDriver()
        Database.Schema.create(driver)
        cut = SqlDelightStore(
            database = Database(driver),
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixJson(),
            databaseCoroutineContext = Dispatchers.Default,
            blockingTransactionCoroutineContext = Dispatchers.Default,
            scope = scope
        )
    }
    afterTest {
        driver.close()
        scope.cancel()
    }
    context("transaction") {
        should("rollback transaction on exception") {
            val timelineEvent1 = TimelineEvent(
                event = Event.MessageEvent(
                    TextMessageEventContent("hi"), EventId("$1e"), UserId("sender", "server"),
                    RoomId("room", "server"), 1234
                ),
                roomId = RoomId("room", "server"),
                eventId = EventId("$1e"),
                previousEventId = null,
                nextEventId = EventId("$2e"),
                gap = null
            )
            val timelineEvent2 = TimelineEvent(
                event = Event.MessageEvent(
                    TextMessageEventContent("ok"), EventId("$2e"), UserId("sender", "server"),
                    RoomId("room", "server"), 1234
                ),
                roomId = RoomId("room", "server"),
                eventId = EventId("$2e"),
                previousEventId = EventId("$1e"),
                nextEventId = null,
                gap = TimelineEvent.Gap.GapAfter("batch")
            )
            cut.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2))

            Database(driver).roomTimelineQueries.getTimelineEvent(EventId("$1e").full, RoomId("room", "server").full)
                .executeAsOne()
                .shouldBe("""{"event":{"content":{"body":"hi","msgtype":"m.text"},"event_id":"$1e","sender":"@sender:server","room_id":"!room:server","origin_server_ts":1234,"type":"m.room.message"},"roomId":"!room:server","eventId":"$1e","nextEventId":"$2e"}""")
            Database(driver).roomTimelineQueries.getTimelineEvent(EventId("$2e").full, RoomId("room", "server").full)
                .executeAsOne()
                .shouldBe("""{"event":{"content":{"body":"ok","msgtype":"m.text"},"event_id":"$2e","sender":"@sender:server","room_id":"!room:server","origin_server_ts":1234,"type":"m.room.message"},"roomId":"!room:server","eventId":"$2e","previousEventId":"$1e","gap":{"position":"after","batch":"batch"}}""")

            shouldThrow<IllegalArgumentException> {
                cut.transaction {
                    cut.roomTimeline.update(EventId("$1e"), RoomId("room", "server")) {
                        timelineEvent1.copy(nextEventId = EventId("$3e"))
                    }
                    cut.roomTimeline.update(EventId("$2e"), RoomId("room", "server")) {
                        timelineEvent2.copy(gap = null)
                    }
                    throw IllegalArgumentException("oh no")
                }
            }
            cut.roomTimeline.get(EventId("$1e"), RoomId("room", "server")) shouldBe timelineEvent1
            cut.roomTimeline.get(EventId("$2e"), RoomId("room", "server")) shouldBe timelineEvent2

            Database(driver).roomTimelineQueries.getTimelineEvent(EventId("$1e").full, RoomId("room", "server").full)
                .executeAsOne()
                .shouldBe("""{"event":{"content":{"body":"hi","msgtype":"m.text"},"event_id":"$1e","sender":"@sender:server","room_id":"!room:server","origin_server_ts":1234,"type":"m.room.message"},"roomId":"!room:server","eventId":"$1e","nextEventId":"$2e"}""")
            Database(driver).roomTimelineQueries.getTimelineEvent(EventId("$2e").full, RoomId("room", "server").full)
                .executeAsOne()
                .shouldBe("""{"event":{"content":{"body":"ok","msgtype":"m.text"},"event_id":"$2e","sender":"@sender:server","room_id":"!room:server","origin_server_ts":1234,"type":"m.room.message"},"roomId":"!room:server","eventId":"$2e","previousEventId":"$1e","gap":{"position":"after","batch":"batch"}}""")
        }
    }
})