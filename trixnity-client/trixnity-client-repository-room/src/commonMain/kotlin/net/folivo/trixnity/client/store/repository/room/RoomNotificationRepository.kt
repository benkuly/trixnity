package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredNotification
import net.folivo.trixnity.client.store.repository.NotificationRepository
import net.folivo.trixnity.core.model.RoomId

@Entity(
    tableName = "Notification",
    primaryKeys = ["id"]
)
data class RoomNotification(
    val id: String,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM Notification")
    suspend fun getAll(): List<RoomNotification>

    @Query("SELECT * FROM Notification WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotification)

    @Query("DELETE FROM Notification WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM Notification WHERE roomId = :roomId ")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM Notification")
    suspend fun deleteAll()
}

internal class RoomNotificationRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationRepository {

    private val dao = db.notification()

    override suspend fun getAll(): List<StoredNotification> = withRoomRead {
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun get(key: String): StoredNotification? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: String, value: StoredNotification) = withRoomWrite {
        dao.insert(
            RoomNotification(
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
