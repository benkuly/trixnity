package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.StoredRoomKeyRequest
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository

@Entity(tableName = "RoomKeyRequest")
data class RoomRoomKeyRequest(
    @PrimaryKey val id: String,
    val value: String,
)

@Dao
interface RoomKeyRequestDao {
    @Query("SELECT * FROM RoomKeyRequest WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomRoomKeyRequest?

    @Query("SELECT * FROM RoomKeyRequest")
    suspend fun getAll(): List<RoomRoomKeyRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomKeyRequest)

    @Query("DELETE FROM RoomKeyRequest WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM RoomKeyRequest")
    suspend fun deleteAll()
}

internal class RoomRoomKeyRequestRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomKeyRequestRepository {

    private val dao = db.roomKeyRequest()

    override suspend fun get(key: String): StoredRoomKeyRequest? = withRoomRead {
        dao.get(key)?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun getAll(): List<StoredRoomKeyRequest> = withRoomRead {
        dao.getAll().map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: String, value: StoredRoomKeyRequest) = withRoomWrite {
        dao.insert(
            RoomRoomKeyRequest(
                id = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: String) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
