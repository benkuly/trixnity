package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.core.model.UserId

@Entity(tableName = "DeviceKeys")
data class RoomDeviceKeys(
    @PrimaryKey val userId: UserId,
    val value: String,
)

@Dao
interface DeviceKeysDao {
    @Query("SELECT * FROM DeviceKeys WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: UserId): RoomDeviceKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomDeviceKeys)

    @Query("DELETE FROM DeviceKeys WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM DeviceKeys")
    suspend fun deleteAll()
}

internal class RoomDeviceKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : DeviceKeysRepository {
    private val dao = db.deviceKeys()

    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString<Map<String, StoredDeviceKeys>>(entity.value) }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) = withRoomWrite {
        dao.insert(
            RoomDeviceKeys(
                userId = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: UserId) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
