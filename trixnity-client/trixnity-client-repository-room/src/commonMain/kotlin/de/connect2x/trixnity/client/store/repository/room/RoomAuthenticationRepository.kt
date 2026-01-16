package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository

@Entity(tableName = "Authentication")
data class RoomAuthentication(
    @PrimaryKey val id: Long = 0,
    val value: String? = null,
)

@Dao
interface AuthenticationDao {
    @Query("SELECT * FROM Authentication WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomAuthentication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomAuthentication)

    @Query("DELETE FROM Authentication WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Authentication")
    suspend fun deleteAll()
}

internal class RoomAuthenticationRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : AuthenticationRepository {

    private val dao = db.authentication()

    override suspend fun get(key: Long): Authentication? = withRoomRead {
        dao.get(key)?.value?.let { json.decodeFromString(it) }
    }

    override suspend fun save(key: Long, value: Authentication) = withRoomWrite {
        dao.insert(
            RoomAuthentication(
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
