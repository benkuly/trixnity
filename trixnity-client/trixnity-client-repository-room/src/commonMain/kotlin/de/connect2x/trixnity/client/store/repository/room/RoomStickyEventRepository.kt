package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.StickyEventContent
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Entity(
    tableName = "StickyEvent",
    primaryKeys = ["roomId", "type", "sender", "stickyKey"],
)
data class RoomStickyEvent(
    val roomId: RoomId,
    val type: String,
    val sender: UserId,
    val stickyKey: String,
    val eventId: EventId,
    val endTimeMs: Long,
    val value: String,
)

@Dao
interface StickyEventDao {
    @Query("SELECT * FROM StickyEvent WHERE roomId = :roomId AND type = :type")
    suspend fun get(roomId: RoomId, type: String): List<RoomStickyEvent>

    @Query("SELECT * FROM StickyEvent WHERE roomId = :roomId AND type = :type AND sender = :sender AND stickyKey = :stickyKey LIMIT 1")
    suspend fun get(roomId: RoomId, type: String, sender: UserId, stickyKey: String): RoomStickyEvent?

    @Query("SELECT * FROM StickyEvent WHERE endTimeMs < :before")
    suspend fun getByEndTimeBefore(before: Long): List<RoomStickyEvent>

    @Query("SELECT * FROM StickyEvent WHERE roomId = :roomId AND eventId = :eventId LIMIT 1")
    suspend fun getByEventId(roomId: RoomId, eventId: EventId): RoomStickyEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomStickyEvent)

    @Query("DELETE FROM StickyEvent WHERE roomId = :roomId AND type = :type AND sender = :sender AND stickyKey = :stickyKey")
    suspend fun delete(roomId: RoomId, type: String, sender: UserId, stickyKey: String)

    @Query("DELETE FROM StickyEvent")
    suspend fun deleteAll()

    @Query("DELETE FROM StickyEvent WHERE roomId = :roomId")
    suspend fun deleteByRoomId(roomId: RoomId)
}

@MSC4354
internal class RoomStickyEventRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : StickyEventRepository {
    companion object {
        private const val STICKY_KEY_NULL = "NULL"
        private const val STICKY_KEY_NON_NULL_PREFIX = "V-"
        private fun dbStickyKey(stickyKey: String?): String =
            stickyKey?.let { STICKY_KEY_NON_NULL_PREFIX + it } ?: STICKY_KEY_NULL

        private fun originalStickyKey(stickyKey: String): String? =
            if (stickyKey == STICKY_KEY_NULL) null
            else stickyKey.removePrefix(STICKY_KEY_NON_NULL_PREFIX)
    }

    private val dao = db.stickyRoomEvent()

    override suspend fun get(firstKey: StickyEventRepositoryFirstKey): Map<StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>> =
        withRoomRead {
            dao.get(firstKey.roomId, firstKey.type)
                .associate {
                    StickyEventRepositorySecondKey(
                        it.sender,
                        originalStickyKey(it.stickyKey)
                    ) to json.decodeFromString(StoredStickyEvent.Serializer, it.value)
                }
        }

    override suspend fun get(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey
    ): StoredStickyEvent<StickyEventContent>? = withRoomRead {
        dao.get(
            firstKey.roomId,
            firstKey.type,
            secondKey.sender,
            dbStickyKey(secondKey.stickyKey)
        )?.let {
            json.decodeFromString(StoredStickyEvent.Serializer, it.value)
        }
    }

    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>> =
        withRoomRead {
            dao.getByEndTimeBefore(before.toEpochMilliseconds())
                .map {
                    StickyEventRepositoryFirstKey(
                        it.roomId,
                        it.type
                    ) to StickyEventRepositorySecondKey(
                        it.sender,
                        originalStickyKey(it.stickyKey)
                    )
                }.toSet()
        }

    override suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>? =
        withRoomRead {
            dao.getByEventId(roomId, eventId)
                ?.let {
                    StickyEventRepositoryFirstKey(
                        it.roomId,
                        it.type
                    ) to StickyEventRepositorySecondKey(
                        it.sender,
                        originalStickyKey(it.stickyKey)
                    )
                }
        }

    override suspend fun save(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey,
        value: StoredStickyEvent<StickyEventContent>
    ): Unit =
        withRoomWrite {
            dao.insert(
                RoomStickyEvent(
                    roomId = firstKey.roomId,
                    type = firstKey.type,
                    sender = secondKey.sender,
                    stickyKey = dbStickyKey(secondKey.stickyKey),
                    eventId = value.event.id,
                    endTimeMs = value.endTime.toEpochMilliseconds(),
                    value = json.encodeToString(StoredStickyEvent.Serializer, value),
                )
            )
        }

    override suspend fun delete(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey
    ): Unit =
        withRoomWrite {
            dao.delete(
                firstKey.roomId,
                firstKey.type,
                secondKey.sender,
                dbStickyKey(secondKey.stickyKey)
            )
        }

    override suspend fun deleteAll(): Unit = withRoomWrite {
        dao.deleteAll()
    }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withRoomWrite {
        dao.deleteByRoomId(roomId)
    }

}
