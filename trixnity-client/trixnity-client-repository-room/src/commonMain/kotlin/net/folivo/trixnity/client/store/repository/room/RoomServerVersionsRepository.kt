package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.ServerVersions
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository

@Entity(tableName = "ServerVersions")
data class RoomServerVersions(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface ServerVersionsDao {
    @Query("SELECT * FROM ServerVersions WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomServerVersions?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomServerVersions)

    @Query("DELETE FROM ServerVersions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ServerVersions")
    suspend fun deleteAll()
}

internal class RoomServerVersionsRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : ServerVersionsRepository {
    private val dao = db.serverVersions()

    override suspend fun get(key: Long): ServerVersions? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    override suspend fun save(key: Long, value: ServerVersions) {
        dao.insert(
            RoomServerVersions(
                id = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: Long) {
        dao.delete(key)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
