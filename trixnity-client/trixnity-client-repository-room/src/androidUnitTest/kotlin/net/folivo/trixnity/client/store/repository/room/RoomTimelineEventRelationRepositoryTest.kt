package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTimelineEventRelationRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomTimelineEventRelationRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomTimelineEventRelationRepository(db, createMatrixEventJson())
    }

    private fun TimelineEventRelation.key() =
        TimelineEventRelationKey(relatesTo.eventId, roomId, relatesTo.relationType)

    @Test
    fun `Save, get and delete`() = runTest {
        val relation1 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$1event"),
            RelatesTo.Reference(EventId("\$relatedEvent1"))
        )
        val relation2 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$2event"),
            RelatesTo.Unknown(
                JsonObject(mapOf("event_id" to JsonPrimitive("\$relatedEvent1"), "rel_type" to JsonPrimitive("bla"))),
                EventId("\$relatedEvent1"),
                RelationType.Unknown("bla"),
                null
            )
        )
        val relation3 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$3event"),
            RelatesTo.Unknown(
                JsonObject(mapOf("event_id" to JsonPrimitive("\$relatedEvent1"), "rel_type" to JsonPrimitive("bla"))),
                EventId("\$relatedEvent1"),
                RelationType.Unknown("bla"),
                null
            )
        )

        repo.save(
            relation1.key(),
            relation1.eventId,
            relation1
        )
        repo.save(
            relation2.key(),
            relation2.eventId,
            relation2
        )
        repo.save(
            relation3.key(),
            relation3.eventId,
            relation3
        )

        repo.get(relation1.key()) shouldBe mapOf(
            relation1.eventId to relation1
        )
        repo.get(relation2.key()) shouldBe mapOf(
            relation2.eventId to relation2,
            relation3.eventId to relation3,
        )
        repo.get(relation1.key(), relation1.eventId) shouldBe relation1

        repo.delete(relation2.key(), relation2.eventId)
        repo.get(relation2.key()) shouldBe mapOf(
            relation3.eventId to relation3,
        )

        repo.delete(relation1.key(), relation1.eventId)
        repo.get(relation1.key(), relation1.eventId) shouldBe null
    }

    @Test
    fun deleteByRoomId() = runTest {
        val relation1 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent1"))
            )
        val relation2 =
            TimelineEventRelation(
                RoomId("room2", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent2"))
            )
        val relation3 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent3"))
            )
        val relation4 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent24"))
            )

        repo.save(
            relation1.key(),
            relation1.eventId,
            relation1
        )
        repo.save(
            relation2.key(),
            relation2.eventId,
            relation2
        )
        repo.save(
            relation3.key(),
            relation3.eventId,
            relation3
        )
        repo.save(
            relation4.key(),
            relation4.eventId,
            relation4
        )

        repo.deleteByRoomId(RoomId("room1", "server"))
        repo.get(
            relation1.key(),
            relation1.eventId,
        ) shouldBe null
        repo.get(
            relation3.key(),
            relation3.eventId,
        ) shouldBe null
        repo.get(
            relation4.key(),
            relation4.eventId,
        ) shouldBe null

        repo.get(
            relation2.key(),
            relation2.eventId,
        ) shouldBe relation2
    }
}
