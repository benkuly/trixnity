package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType
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
        repo = RoomTimelineEventRelationRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = TimelineEventRelationKey(
            EventId("\$relatedEvent1"),
            RoomId("room1", "server")
        )
        val key2 = TimelineEventRelationKey(
            EventId("\$relatedEvent2"),
            RoomId("room1", "server")
        )
        val relation1 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$1event"),
            RelationType.Reference,
            EventId("\$relatedEvent1")
        )
        val relation2 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$2event"),
            RelationType.Unknown("bla"),
            EventId("\$relatedEvent1")
        )
        val relation3 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$3event"),
            RelationType.Unknown("bli"),
            EventId("\$relatedEvent1")
        )
        val relation4 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$4event"),
            RelationType.Reference,
            EventId("\$relatedEvent2")
        )

        repo.save(
            key1,
            mapOf(
                RelationType.Reference to setOf(relation1),
                RelationType.Unknown("bla") to setOf(relation2),
                RelationType.Unknown("bli") to setOf(relation3)
            )
        )
        repo.saveBySecondKey(key2, RelationType.Reference, setOf(relation4))

        repo.get(key1) shouldBe mapOf(
            RelationType.Reference to setOf(relation1),
            RelationType.Unknown("bla") to setOf(relation2),
            RelationType.Unknown("bli") to setOf(relation3)
        )
        repo.getBySecondKey(key1, RelationType.Unknown("bla")) shouldBe setOf(relation2)
        repo.getBySecondKey(key2, RelationType.Reference) shouldBe setOf(relation4)

        repo.deleteBySecondKey(key1, RelationType.Unknown("bla"))
        repo.get(key1) shouldBe mapOf(
            RelationType.Reference to setOf(relation1),
            RelationType.Unknown("bli") to setOf(relation3)
        )
        repo.delete(key2)
        repo.get(key2) shouldBe null
    }
}
