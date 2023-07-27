package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val relatesTo: String,
)

@Dao
internal interface TimelineEventRelationDao {
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

internal class RoomTimelineEventRelationRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : TimelineEventRelationRepository {

    private val dao = db.timelineEventRelation()

    override suspend fun get(firstKey: TimelineEventRelationKey): Map<EventId, TimelineEventRelation> =
        dao.get(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType).associate {
            it.eventId to TimelineEventRelation(
                roomId = it.roomId,
                eventId = it.eventId,
                relatesTo = json.decodeFromString(it.relatesTo),
            )
        }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        dao.delete(roomId)
    }

    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
    ): TimelineEventRelation? =
        dao.get(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType, secondKey)?.let {
            TimelineEventRelation(
                roomId = firstKey.roomId,
                eventId = it.eventId,
                relatesTo = json.decodeFromString(it.relatesTo),
            )
        }

    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
        value: TimelineEventRelation,
    ) {
        dao.insert(
            RoomTimelineEventRelation(
                roomId = value.roomId,
                eventId = value.eventId,
                relationType = value.relatesTo.relationType,
                relatedEventId = value.relatesTo.eventId,
                relatesTo = json.encodeToString(value.relatesTo),
            )
        )
    }

    override suspend fun delete(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ) {
        dao.delete(firstKey.relatedEventId, firstKey.roomId, firstKey.relationType, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
