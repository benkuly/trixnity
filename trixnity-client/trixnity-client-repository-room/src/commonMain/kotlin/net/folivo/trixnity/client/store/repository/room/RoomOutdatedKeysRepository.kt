package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.core.model.UserId

@Entity(tableName = "OutdatedKeys")
data class RoomOutdatedKeys(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface OutdatedKeysDao {
    @Query("SELECT * FROM OutdatedKeys WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomOutdatedKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOutdatedKeys)

    @Query("DELETE FROM OutdatedKeys WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM OutdatedKeys")
    suspend fun deleteAll()
}

internal class RoomOutdatedKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : OutdatedKeysRepository {
    private val dao = db.outdatedKeys()

    override suspend fun get(key: Long): Set<UserId>? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: Long, value: Set<UserId>) = withRoomWrite {
        dao.insert(
            RoomOutdatedKeys(
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
