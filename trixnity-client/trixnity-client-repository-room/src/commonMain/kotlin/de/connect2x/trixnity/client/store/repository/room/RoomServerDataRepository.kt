package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.repository.ServerDataRepository

@Entity(tableName = "ServerData")
data class RoomServerData(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface ServerDataDao {
    @Query("SELECT * FROM ServerData WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomServerData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomServerData)

    @Query("DELETE FROM ServerData WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ServerData")
    suspend fun deleteAll()
}

internal class RoomServerDataRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : ServerDataRepository {
    private val dao = db.serverData()

    override suspend fun get(key: Long): ServerData? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: Long, value: ServerData) = withRoomWrite {
        dao.insert(
            RoomServerData(
                id = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: Long) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
