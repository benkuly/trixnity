package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.repository.OlmAccountRepository

@Entity(tableName = "OlmAccount")
data class RoomOlmAccount(
    @PrimaryKey val id: Long,
    val pickled: String,
)

@Dao
interface OlmAccountDao {
    @Query("SELECT * FROM OlmAccount WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomOlmAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOlmAccount)

    @Query("DELETE FROM OlmAccount WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM OlmAccount")
    suspend fun deleteAll()
}

internal class RoomOlmAccountRepository(
    db: TrixnityRoomDatabase,
) : OlmAccountRepository {
    private val dao = db.olmAccount()

    override suspend fun get(key: Long): String? = withRoomRead {
        dao.get(key)?.pickled
    }

    override suspend fun save(key: Long, value: String) = withRoomWrite {
        dao.insert(
            RoomOlmAccount(
                id = key,
                pickled = value,
            )
        )
    }

    override suspend fun delete(key: Long) = withRoomWrite {
        dao.delete(id = key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
