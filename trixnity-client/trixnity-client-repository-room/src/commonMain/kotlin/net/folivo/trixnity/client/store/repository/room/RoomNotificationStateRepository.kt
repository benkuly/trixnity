package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredNotificationState
import net.folivo.trixnity.client.store.repository.NotificationStateRepository
import net.folivo.trixnity.core.model.RoomId

@Entity(
    tableName = "NotificationState",
    primaryKeys = ["roomId"]
)
data class RoomNotificationState(
    val roomId: RoomId,
    val value: String,
)

@Dao
interface NotificationStateDao {
    @Query("SELECT * FROM NotificationState")
    suspend fun getAll(): List<RoomNotificationState>

    @Query("SELECT * FROM NotificationState WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomNotificationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomNotificationState)

    @Query("DELETE FROM NotificationState WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM NotificationState")
    suspend fun deleteAll()
}

internal class RoomNotificationStateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : NotificationStateRepository {

    private val dao = db.notificationState()

    override suspend fun getAll(): List<StoredNotificationState> = withRoomRead {
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun get(key: RoomId): StoredNotificationState? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: RoomId, value: StoredNotificationState) = withRoomWrite {
        dao.insert(
            RoomNotificationState(
                roomId = value.roomId,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: RoomId) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
