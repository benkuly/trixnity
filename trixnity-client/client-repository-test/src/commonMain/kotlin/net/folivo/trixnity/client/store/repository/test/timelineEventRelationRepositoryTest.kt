package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType
import org.koin.core.Koin


fun ShouldSpec.timelineEventRelationRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: TimelineEventRelationRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    fun TimelineEventRelation.key() = TimelineEventRelationKey(relatedEventId, roomId, relationType)
    should("timelineEventRelationRepositoryTest: save, get and delete") {
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
            EventId("\$relatedEvent1"),
        )
        val relation3 = TimelineEventRelation(
            RoomId("room1", "server"),
            EventId("$3event"),
            RelationType.Unknown("bla"),
            EventId("\$relatedEvent1"),
        )

        rtm.writeTransaction {
            cut.save(
                relation1.key(),
                relation1.eventId,
                relation1
            )
            cut.save(
                relation2.key(),
                relation2.eventId,
                relation2
            )
            cut.save(
                relation3.key(),
                relation3.eventId,
                relation3
            )

            cut.get(relation1.key()) shouldBe mapOf(
                relation1.eventId to relation1
            )
            cut.get(relation2.key()) shouldBe mapOf(
                relation2.eventId to relation2,
                relation3.eventId to relation3,
            )
            cut.get(relation1.key(), relation1.eventId) shouldBe relation1

            cut.delete(relation2.key(), relation2.eventId)
            cut.get(relation2.key()) shouldBe mapOf(
                relation3.eventId to relation3,
            )

            cut.delete(relation1.key(), relation1.eventId)
            cut.get(relation1.key(), relation1.eventId) shouldBe null
        }
    }
    should("timelineEventRelationRepositoryTest: deleteByRoomId") {
        val relation1 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent1")
            )
        val relation2 =
            TimelineEventRelation(
                RoomId("room2", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent2")
            )
        val relation3 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent3")
            )
        val relation4 =
            TimelineEventRelation(
                RoomId("room1", "server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent24")
            )

        rtm.writeTransaction {
            cut.save(
                relation1.key(),
                relation1.eventId,
                relation1
            )
            cut.save(
                relation2.key(),
                relation2.eventId,
                relation2
            )
            cut.save(
                relation3.key(),
                relation3.eventId,
                relation3
            )
            cut.save(
                relation4.key(),
                relation4.eventId,
                relation4
            )

            cut.deleteByRoomId(RoomId("room1", "server"))
            cut.get(
                relation1.key(),
                relation1.eventId,
            ) shouldBe null
            cut.get(
                relation3.key(),
                relation3.eventId,
            ) shouldBe null
            cut.get(
                relation4.key(),
                relation4.eventId,
            ) shouldBe null

            cut.get(
                relation2.key(),
                relation2.eventId,
            ) shouldBe relation2
        }
    }
}