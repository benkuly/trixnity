package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType
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
    override suspend fun get(firstKey: TimelineEventRelationKey): Map<EventId, TimelineEventRelation> =
        withExposedRead {
            ExposedTimelineEventRelation.selectAll().where {
                ExposedTimelineEventRelation.relatedEventId.eq(firstKey.relatedEventId.full) and
                        ExposedTimelineEventRelation.roomId.eq(firstKey.roomId.full) and
                        ExposedTimelineEventRelation.relationType.eq(firstKey.relationType.name)
            }.associate {
                val eventId = EventId(it[ExposedTimelineEventRelation.eventId])
                eventId to TimelineEventRelation(
                    roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                    eventId = eventId,
                    relationType = RelationType.of(it[ExposedTimelineEventRelation.relationType]),
                    relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
                )
            }
        }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withExposedWrite {
        ExposedTimelineEventRelation.deleteWhere { ExposedTimelineEventRelation.roomId.eq(roomId.full) }
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ): TimelineEventRelation? = withExposedRead {
        ExposedTimelineEventRelation.selectAll().where {
            ExposedTimelineEventRelation.relatedEventId.eq(firstKey.relatedEventId.full) and
                    ExposedTimelineEventRelation.roomId.eq(firstKey.roomId.full) and
                    ExposedTimelineEventRelation.relationType.eq(firstKey.relationType.name) and
                    ExposedTimelineEventRelation.eventId.eq(secondKey.full)
        }.firstOrNull()?.let {
            TimelineEventRelation(
                roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                eventId = EventId(it[ExposedTimelineEventRelation.eventId]),
                relationType = RelationType.of(it[ExposedTimelineEventRelation.relationType]),
                relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
            )
        }
    }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
        value: TimelineEventRelation
    ): Unit = withExposedWrite {
        ExposedTimelineEventRelation.upsert {
            it[ExposedTimelineEventRelation.eventId] = value.eventId.full
            it[ExposedTimelineEventRelation.roomId] = value.roomId.full
            it[ExposedTimelineEventRelation.relationType] = value.relationType.name
            it[ExposedTimelineEventRelation.relatedEventId] = value.relatedEventId.full
        }
    }

    override suspend fun delete(firstKey: TimelineEventRelationKey, secondKey: EventId): Unit =
        withExposedWrite {
            ExposedTimelineEventRelation.deleteWhere {
                relatedEventId.eq(firstKey.relatedEventId.full) and
                        roomId.eq(firstKey.roomId.full) and
                        relationType.eq(firstKey.relationType.name) and
                        eventId.eq(secondKey.full)
            }
        }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedTimelineEventRelation.deleteAll()
    }
}