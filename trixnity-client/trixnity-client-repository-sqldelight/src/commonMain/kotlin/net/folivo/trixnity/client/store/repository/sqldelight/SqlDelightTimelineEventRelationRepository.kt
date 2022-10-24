package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.sqldelight.RoomTimelineQueries
import net.folivo.trixnity.client.store.sqldelight.Sql_room_timeline_event_relation
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType
import kotlin.coroutines.CoroutineContext

internal class SqlDelightTimelineEventRelationRepository(
    private val db: RoomTimelineQueries,
    private val context: CoroutineContext
) : TimelineEventRelationRepository {
    override suspend fun get(key: TimelineEventRelationKey): Map<RelationType, Set<TimelineEventRelation>>? =
        withContext(context) {
            db.getTimelineEventRelation(key.relatedEventId.full, key.roomId.full).executeAsList()
                .groupBy { it.relation_type }
                .map { entry ->
                    val relationType = RelationType.of(entry.key)
                    relationType to entry.value.map {
                        TimelineEventRelation(
                            roomId = RoomId(it.room_id),
                            eventId = EventId(it.event_id),
                            relationType = relationType,
                            relatedEventId = EventId(it.related_event_id),
                        )
                    }.toSet()
                }.toMap().ifEmpty { null }
        }

    override suspend fun save(key: TimelineEventRelationKey, value: Map<RelationType, Set<TimelineEventRelation>>) =
        withContext(context) {
            value.forEach { saveBySecondKey(key, it.key, it.value) }
        }

    override suspend fun delete(key: TimelineEventRelationKey) = withContext(context) {
        db.deleteTimelineEventRelation(key.relatedEventId.full, key.roomId.full)
    }

    override suspend fun getBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ): Set<TimelineEventRelation>? = withContext(context) {
        db.getTimelineEventRelationByRelationType(firstKey.relatedEventId.full, firstKey.roomId.full, secondKey.name)
            .executeAsList()
            .map {
                TimelineEventRelation(
                    roomId = RoomId(it.room_id),
                    eventId = EventId(it.event_id),
                    relationType = RelationType.of(it.relation_type),
                    relatedEventId = EventId(it.related_event_id),
                )
            }.toSet().ifEmpty { null }
    }

    override suspend fun saveBySecondKey(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
        value: Set<TimelineEventRelation>
    ) = withContext(context) {
        value.forEach {
            db.saveTimelineEventRelation(
                Sql_room_timeline_event_relation(
                    event_id = it.eventId.full,
                    room_id = it.roomId.full,
                    relation_type = it.relationType.name,
                    related_event_id = it.relatedEventId.full,
                )
            )
        }
    }

    override suspend fun deleteBySecondKey(firstKey: TimelineEventRelationKey, secondKey: RelationType) =
        withContext(context) {
            db.deleteTimelineEventRelationByRelationType(
                firstKey.relatedEventId.full,
                firstKey.roomId.full,
                secondKey.name
            )
        }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllTimelineEventRelations()
    }
}