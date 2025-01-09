package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId

@Entity(tableName = "Room")
data class RoomRoom(
    @PrimaryKey val roomId: RoomId,
    val value: String,
)

@Dao
interface RoomRoomDao {
    @Query("SELECT * FROM Room WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomRoom?

    @Query("SELECT * FROM Room")
    suspend fun getAll(): List<RoomRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoom)

    @Query("DELETE FROM Room WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM Room")
    suspend fun deleteAll()
}

internal class RoomRoomRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomRepository {
    private val dao = db.room()

    override suspend fun get(key: RoomId): Room? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun getAll(): List<Room> = withRoomRead {
        dao.getAll()
            .map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: RoomId, value: Room) = withRoomWrite {
        dao.insert(
            RoomRoom(
                roomId = key,
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
