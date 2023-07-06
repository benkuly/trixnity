package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType
import org.koin.core.Koin


fun ShouldSpec.timelineEventRelationRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: TimelineEventRelationRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("timelineEventRelationRepositoryTest: save, get and delete") {
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
                RelationType.Reference,
                setOf(relation1),
            )
            cut.save(
                key1,
                RelationType.Unknown("bla"),
                setOf(relation2)
            )
            cut.save(
                key1,
                RelationType.Unknown("bli"),
                setOf(relation3)
            )
            cut.save(key2, RelationType.Reference, setOf(relation4))

            cut.get(key1) shouldBe mapOf(
                RelationType.Reference to setOf(relation1),
                RelationType.Unknown("bla") to setOf(relation2),
                RelationType.Unknown("bli") to setOf(relation3)
            )
            cut.get(key1, RelationType.Unknown("bla")) shouldBe setOf(relation2)
            cut.get(key2, RelationType.Reference) shouldBe setOf(relation4)

            cut.delete(key1, RelationType.Unknown("bla"))
            cut.get(key1) shouldBe mapOf(
                RelationType.Reference to setOf(relation1),
                RelationType.Unknown("bli") to setOf(relation3)
            )
            cut.delete(key2, RelationType.Reference)
            cut.get(key2, RelationType.Reference) shouldBe null
        }
    }
    should("timelineEventRelationRepositoryTest: deleteByRoomId") {
        val key1 = TimelineEventRelationKey(EventId("\$relatedEvent1"), RoomId("room1", "server"))
        val key2 = TimelineEventRelationKey(EventId("\$relatedEvent2"), RoomId("room2", "server"))
        val key3 = TimelineEventRelationKey(EventId("\$relatedEvent3"), RoomId("room1", "server"))
        val relations1 = setOf(
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent1")
            ), TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent24")
            )
        )
        val relations2 = setOf(
            TimelineEventRelation(
                RoomId("room2", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent2")
            )
        )
        val relations3 = setOf(
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent3")
            )
        )

        rtm.writeTransaction {
            cut.save(key1, RelationType.Reference, relations1)
            cut.save(key2, RelationType.Reference, relations2)
            cut.save(key3, RelationType.Reference, relations3)

            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(key1).shouldBeEmpty()
            cut.get(key2) shouldBe mapOf(RelationType.Reference to relations2)
            cut.get(key3).shouldBeEmpty()
        }
    }
}