package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType

@Entity(
    tableName = "TimelineEventRelation",
    primaryKeys = ["roomId", "eventId", "relationType", "relatedEventId"]
)
internal data class RoomTimelineEventRelation(
    val roomId: RoomId,
    val eventId: EventId,
    val relationType: RelationType,
    val relatedEventId: EventId,
)

@Dao
internal interface TimelineEventRelationDao {
    @Query(
        """
        SELECT * FROM TimelineEventRelation
        WHERE relatedEventId = :relatedEventId
        AND roomId = :roomId
        """
    )
    suspend fun get(
        relatedEventId: EventId,
        roomId: RoomId,
    ): List<RoomTimelineEventRelation>

    @Query(
        """
        SELECT * FROM TimelineEventRelation
        WHERE relatedEventId = :relatedEventId
        AND roomId = :roomId
        AND relationType = :relationType
        """
    )
    suspend fun get(
        relatedEventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): List<RoomTimelineEventRelation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomTimelineEventRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoomTimelineEventRelation>)

    @Query(
        """
        DELETE FROM TimelineEventRelation
        WHERE roomId = :roomId
        """
    )
    suspend fun delete(roomId: RoomId)

    @Query(
        """
        DELETE FROM TimelineEventRelation
        WHERE relatedEventId = :relatedEventId
        AND roomId = :roomId
        """
    )
    suspend fun delete(relatedEventId: EventId, roomId: RoomId)

    @Query(
        """
        DELETE FROM TimelineEventRelation
        WHERE relatedEventId = :relatedEventId
        AND roomId = :roomId
        AND relationType = :relationType
        """
    )
    suspend fun delete(relatedEventId: EventId, roomId: RoomId, relationType: RelationType)

    @Query("DELETE FROM TimelineEventRelation")
    suspend fun deleteAll()
}

internal class RoomTimelineEventRelationRepository(
    db: TrixnityRoomDatabase,
) : TimelineEventRelationRepository {

    private val dao = db.timelineEventRelation()

    override suspend fun get(firstKey: TimelineEventRelationKey): Map<RelationType, Set<TimelineEventRelation>> =
        dao.get(firstKey.relatedEventId, firstKey.roomId)
            .groupBy { it.relationType }
            .map { (relationType, entities) ->
                relationType to entities.map { entity ->
                    TimelineEventRelation(
                        roomId = entity.roomId,
                        eventId = entity.eventId,
                        relationType = relationType,
                        relatedEventId = entity.relatedEventId,
                    )
                }.toSet()
            }.toMap()

    override suspend fun deleteByRoomId(roomId: RoomId) {
        dao.delete(roomId)
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
    ): Set<TimelineEventRelation>? =
        dao.get(firstKey.relatedEventId, firstKey.roomId, secondKey).map { entity ->
            TimelineEventRelation(
                roomId = firstKey.roomId,
                eventId = entity.eventId,
                relationType = secondKey,
                relatedEventId = firstKey.relatedEventId,
            )
        }.toSet().ifEmpty { null }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType,
        value: Set<TimelineEventRelation>,
    ) {
        dao.insertAll(
            value.map { relation ->
                RoomTimelineEventRelation(
                    roomId = firstKey.roomId,
                    eventId = relation.eventId,
                    relationType = secondKey,
                    relatedEventId = firstKey.relatedEventId,
                )
            }
        )
    }

    override suspend fun delete(
        firstKey: TimelineEventRelationKey,
        secondKey: RelationType
    ) {
        dao.delete(firstKey.relatedEventId, firstKey.roomId, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
