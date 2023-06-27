package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedTimelineEventRelation : Table("room_timeline_event_relation") {
    val roomId = varchar("room_id", length = 128)
    val eventId = varchar("event_id", length = 128)
    val relationType = varchar("relation_type", length = 128)
    val relatedEventId = varchar("related_event_id", length = 128)
    override val primaryKey = PrimaryKey(roomId, eventId, relationType, relatedEventId)
}

internal class ExposedTimelineEventRelationRepository : TimelineEventRelationRepository {
    override suspend fun get(key: TimelineEventRelationKey): Map<RelationType, Set<TimelineEventRelation>>? =
        withExposedRead {
            ExposedTimelineEventRelation.select {
                ExposedTimelineEventRelation.relatedEventId.eq(key.relatedEventId.full) and
                        ExposedTimelineEventRelation.roomId.eq(key.roomId.full)
            }.groupBy { it[ExposedTimelineEventRelation.relationType] }
                .map { entry ->
                    val relationType = RelationType.of(entry.key)
                    relationType to entry.value.map {
                        TimelineEventRelation(
                            roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                            eventId = EventId(it[ExposedTimelineEventRelation.eventId]),
                            relationType = relationType,
                            relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
                        )
                    }.toSet()
                }.toMap().ifEmpty { null }
        }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withExposedWrite {
        ExposedTimelineEventRelation.deleteWhere { ExposedTimelineEventRelation.roomId.eq(roomId.full) }
    }

    override suspend fun save(
        key: TimelineEventRelationKey,
        value: Map<RelationType, Set<TimelineEventRelation>>
    ): Unit = withExposedWrite {
        ExposedTimelineEventRelation.batchReplace(value.entries.flatMap { it.value }) {
            this[ExposedTimelineEventRelation.eventId] = it.eventId.full
            this[ExposedTimelineEventRelation.roomId] = it.roomId.full
            this[ExposedTimelineEventRelation.relationType] = it.relationType.name
            this[ExposedTimelineEventRelation.relatedEventId] = it.relatedEventId.full
        }
    }

    override suspend fun delete(key: TimelineEventRelationKey): Unit = withExposedWrite {
        ExposedTimelineEventRelation.deleteWhere {
            relatedEventId.eq(key.relatedEventId.full) and
                    roomId.eq(key.roomId.full)
        }
    }

    override suspend fun getBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ): Set<TimelineEventRelation>? = withExposedRead {
        ExposedTimelineEventRelation.select {
            ExposedTimelineEventRelation.relatedEventId.eq(firstKey.relatedEventId.full) and
                    ExposedTimelineEventRelation.roomId.eq(firstKey.roomId.full) and
                    ExposedTimelineEventRelation.relationType.eq(secondKey.name)
        }.map {
            TimelineEventRelation(
                roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                eventId = EventId(it[ExposedTimelineEventRelation.eventId]),
                relationType = RelationType.of(it[ExposedTimelineEventRelation.relationType]),
                relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
            )
        }.toSet().ifEmpty { null }
    }

    override suspend fun saveBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
        value: Set<TimelineEventRelation>
    ): Unit = withExposedWrite {
        ExposedTimelineEventRelation.batchReplace(value) {
            this[ExposedTimelineEventRelation.eventId] = it.eventId.full
            this[ExposedTimelineEventRelation.roomId] = it.roomId.full
            this[ExposedTimelineEventRelation.relationType] = it.relationType.name
            this[ExposedTimelineEventRelation.relatedEventId] = it.relatedEventId.full
        }
    }

    override suspend fun deleteBySecondKey(firstKey: TimelineEventRelationKey, secondKey: RelationType): Unit =
        withExposedWrite {
            ExposedTimelineEventRelation.deleteWhere {
                relatedEventId.eq(firstKey.relatedEventId.full) and
                        roomId.eq(firstKey.roomId.full) and
                        relationType.eq(secondKey.name)
            }
        }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedTimelineEventRelation.deleteAll()
    }
}