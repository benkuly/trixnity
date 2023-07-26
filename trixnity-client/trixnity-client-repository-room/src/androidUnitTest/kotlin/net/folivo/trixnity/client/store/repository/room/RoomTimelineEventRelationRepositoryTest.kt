package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldBeEmpty
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

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = TimelineEventRelationKey(EventId("\$relatedEvent1"), RoomId("room1", "server"))
        val key2 = TimelineEventRelationKey(EventId("\$relatedEvent2"), RoomId("room1", "server"))
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
                JsonObject(mapOf("event_id" to JsonPrimitive("\$relatedEvent1"), "rel_type" to JsonPrimitive("bli"))),
                EventId("\$relatedEvent1"),
                RelationType.Unknown("bli"),
                null
            )
        )
        val relation4 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$4event"),
            RelatesTo.Reference(EventId("\$relatedEvent2"))
        )

        repo.save(
            key1,
            RelationType.Reference,
            setOf(relation1),
        )
        repo.save(
            key1,
            RelationType.Unknown("bla"),
            setOf(relation2)
        )
        repo.save(
            key1,
            RelationType.Unknown("bli"),
            setOf(relation3)
        )
        repo.save(key2, RelationType.Reference, setOf(relation4))

        repo.get(key1) shouldBe mapOf(
            RelationType.Reference to setOf(relation1),
            RelationType.Unknown("bla") to setOf(relation2),
            RelationType.Unknown("bli") to setOf(relation3)
        )
        repo.get(key1, RelationType.Unknown("bla")) shouldBe setOf(relation2)
        repo.get(key2, RelationType.Reference) shouldBe setOf(relation4)

        repo.delete(key1, RelationType.Unknown("bla"))
        repo.get(key1) shouldBe mapOf(
            RelationType.Reference to setOf(relation1),
            RelationType.Unknown("bli") to setOf(relation3)
        )
        repo.delete(key2, RelationType.Reference)
        repo.get(key2, RelationType.Reference) shouldBe null
    }

    @Test
    fun deleteByRoomId() = runTest {
        val key1 = TimelineEventRelationKey(EventId("\$relatedEvent1"), RoomId("room1", "server"))
        val key2 = TimelineEventRelationKey(EventId("\$relatedEvent2"), RoomId("room2", "server"))
        val key3 = TimelineEventRelationKey(EventId("\$relatedEvent3"), RoomId("room1", "server"))
        val relations1 = setOf(
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent1"))
            ), TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent24"))
            )
        )
        val relations2 = setOf(
            TimelineEventRelation(
                RoomId("room2", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent2"))
            )
        )
        val relations3 = setOf(
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelatesTo.Reference(EventId("\$relatedEvent3"))
            )
        )

        repo.save(key1, RelationType.Reference, relations1)
        repo.save(key2, RelationType.Reference, relations2)
        repo.save(key3, RelationType.Reference, relations3)

        repo.deleteByRoomId(RoomId("room1", "server"))
        repo.get(key1).shouldBeEmpty()
        repo.get(key2) shouldBe mapOf(RelationType.Reference to relations2)
        repo.get(key3).shouldBeEmpty()
    }
}
