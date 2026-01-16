package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType

@Entity(
    tableName = "TimelineEventRelation",
    primaryKeys = ["roomId", "eventId", "relationType", "relatedEventId"]
)
data class RoomTimelineEventRelation(
    val roomId: RoomId,
    val eventId: EventId,
    val relationType: RelationType,
    val relatedEventId: EventId,
)

@Dao
interface TimelineEventRelationDao {
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

    @Query(
        """
        SELECT * FROM TimelineEventRelation
        WHERE relatedEventId = :relatedEventId
        AND roomId = :roomId
        AND relationType = :relationType
        AND eventId = :eventId
        """
    )
    suspend fun get(
        relatedEventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
        eventId: EventId
    ): RoomTimelineEventRelation?

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
        AND eventId = :eventId
        """
    )
    suspend fun delete(relatedEventId: EventId, roomId: RoomId, relationType: RelationType, eventId: EventId)

    @Query("DELETE FROM TimelineEventRelation")
    suspend fun deleteAll()
}

internal class RoomTimelineEventRelationRepository(db: TrixnityRoomDatabase) : TimelineEventRelationRepository {

    private val dao = db.timelineEventRelation()

    override suspend fun get(firstKey: TimelineEventRelationKey): Map<EventId, TimelineEventRelation> = withRoomRead {
        dao.get(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType).associate {
            it.eventId to TimelineEventRelation(
                roomId = it.roomId,
                eventId = it.eventId,
                relationType = it.relationType,
                relatedEventId = it.relatedEventId,
            )
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRoomWrite {
        dao.delete(roomId)
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
    ): TimelineEventRelation? = withRoomRead {
        dao.get(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType, secondKey)?.let {
            TimelineEventRelation(
                roomId = firstKey.roomId,
                eventId = it.eventId,
                relationType = it.relationType,
                relatedEventId = it.relatedEventId,
            )
        }
    }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
        value: TimelineEventRelation,
    ) = withRoomWrite {
        dao.insert(
            RoomTimelineEventRelation(
                roomId = value.roomId,
                eventId = value.eventId,
                relationType = value.relationType,
                relatedEventId = value.relatedEventId,
            )
        )
    }

    override suspend fun delete(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ) = withRoomWrite {
        dao.delete(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType, secondKey)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
