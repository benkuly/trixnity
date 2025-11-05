package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredNotificationUpdate
import net.folivo.trixnity.client.store.repository.NotificationUpdateRepository
import net.folivo.trixnity.core.model.RoomId

@Entity(
    tableName = "NotificationUpdate",
    primaryKeys = ["id"]
)
data class RoomNotificationUpdate(
    val id: String,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationUpdateDao {
    @Query("SELECT * FROM NotificationUpdate")
    suspend fun getAll(): List<RoomNotificationUpdate>

    @Query("SELECT * FROM NotificationUpdate WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomNotificationUpdate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotificationUpdate)

    @Query("DELETE FROM NotificationUpdate WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM NotificationUpdate WHERE roomId = :roomId ")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM NotificationUpdate")
    suspend fun deleteAll()
}

internal class RoomNotificationUpdateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationUpdateRepository {

    private val dao = db.notificationUpdate()

    override suspend fun getAll(): List<StoredNotificationUpdate> = withRoomRead {
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun get(key: String): StoredNotificationUpdate? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: String, value: StoredNotificationUpdate) = withRoomWrite {
        dao.insert(
            RoomNotificationUpdate(
                id = key,
                roomId = value.roomId,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        dao.delete(roomId)
    }

    override suspend fun delete(key: String) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
