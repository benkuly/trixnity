package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import kotlin.time.Instant

@Entity(tableName = "OlmForgetFallbackKeyAfter")
data class RoomOlmForgetFallbackKeyAfter(
    @PrimaryKey val id: Long,
    val instant: Instant,
)

@Dao
interface OlmForgetFallbackKeyAfterDao {
    @Query("SELECT * FROM OlmForgetFallbackKeyAfter WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomOlmForgetFallbackKeyAfter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOlmForgetFallbackKeyAfter)

    @Query("DELETE FROM OlmForgetFallbackKeyAfter WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM OlmForgetFallbackKeyAfter")
    suspend fun deleteAll()
}

internal class RoomOlmForgetFallbackKeyAfterRepository(
    db: TrixnityRoomDatabase,
) : OlmForgetFallbackKeyAfterRepository {
    private val dao = db.olmForgetFallbackKeyAfter()

    override suspend fun get(key: Long): Instant? = withRoomRead {
        dao.get(key)?.instant
    }

    override suspend fun save(key: Long, value: Instant) = withRoomWrite {
        dao.insert(
            RoomOlmForgetFallbackKeyAfter(
                id = key,
                instant = value,
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
