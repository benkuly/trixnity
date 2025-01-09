package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@Entity(
    tableName = "TimelineEvent",
    primaryKeys = ["roomId", "eventId"]
)
data class RoomTimelineEvent(
    val roomId: RoomId,
    val eventId: EventId,
    val value: String,
)

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM TimelineEvent WHERE roomId = :roomId AND eventId = :eventId LIMIT 1")
    suspend fun get(roomId: RoomId, eventId: EventId): RoomTimelineEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomTimelineEvent)

    @Query("DELETE FROM TimelineEvent WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM TimelineEvent WHERE roomId = :roomId AND eventId = :eventId")
    suspend fun delete(roomId: RoomId, eventId: EventId)

    @Query("DELETE FROM TimelineEvent")
    suspend fun deleteAll()
}

internal class RoomTimelineEventRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : TimelineEventRepository {

    private val dao = db.timelineEvent()
    override suspend fun deleteByRoomId(roomId: RoomId) {
        dao.delete(roomId)
    }

    override suspend fun get(key: TimelineEventKey): TimelineEvent? = withRoomRead {
        dao.get(key.roomId, key.eventId)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) = withRoomWrite {
        dao.insert(
            RoomTimelineEvent(
                roomId = key.roomId,
                eventId = key.eventId,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: TimelineEventKey) = withRoomWrite {
        dao.delete(key.roomId, key.eventId)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
