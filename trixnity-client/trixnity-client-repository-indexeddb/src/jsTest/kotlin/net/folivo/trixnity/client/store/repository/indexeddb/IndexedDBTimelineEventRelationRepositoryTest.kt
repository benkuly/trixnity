package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBTimelineEventRelationRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBTimelineEventRelationRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBTimelineEventRelationRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBTimelineEventRelationRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        val key1 = TimelineEventRelationKey(EventId("\$relatedEvent1"), RoomId("room1", "server"))
        val key2 = TimelineEventRelationKey(EventId("\$relatedEvent2"), RoomId("room1", "server"))
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

        rtm.writeTransaction {
            cut.save(
                key1,
                mapOf(
                    RelationType.Reference to setOf(relation1),
                    RelationType.Unknown("bla") to setOf(relation2),
                    RelationType.Unknown("bli") to setOf(relation3)
                )
            )
            cut.saveBySecondKey(key2, RelationType.Reference, setOf(relation4))

            cut.get(key1) shouldBe mapOf(
                RelationType.Reference to setOf(relation1),
                RelationType.Unknown("bla") to setOf(relation2),
                RelationType.Unknown("bli") to setOf(relation3)
            )
            cut.getBySecondKey(key1, RelationType.Unknown("bla")) shouldBe setOf(relation2)
            cut.getBySecondKey(key2, RelationType.Reference) shouldBe setOf(relation4)

            cut.deleteBySecondKey(key1, RelationType.Unknown("bla"))
            cut.get(key1) shouldBe mapOf(
                RelationType.Reference to setOf(relation1),
                RelationType.Unknown("bli") to setOf(relation3)
            )
            cut.delete(key2)
            cut.get(key2).shouldBeEmpty()
        }
    }
})