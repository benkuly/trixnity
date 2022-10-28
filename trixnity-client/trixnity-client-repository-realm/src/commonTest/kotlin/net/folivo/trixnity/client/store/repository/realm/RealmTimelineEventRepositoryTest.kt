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
import java.io.File

class RealmTimelineEventRepositoryTest : ShouldSpec({
    timeout = 10_000
    val realmDbPath = "build/${uuid4()}"
    lateinit var realm: Realm
    lateinit var cut: RealmTimelineEventRepository

    beforeTest {
        File(realmDbPath).deleteRecursively()
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmTimelineEvent::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmTimelineEventRepository(createMatrixEventJson())
    }
    afterTest {
        File(realmDbPath).deleteRecursively()
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
})