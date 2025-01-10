package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.core.model.UserId

@Entity(tableName = "CrossSigningKeys")
data class RoomCrossSigningKeys(
    @PrimaryKey val userId: UserId,
    val value: String,
)

@Dao
interface CrossSigningKeysDao {
    @Query("SELECT * FROM CrossSigningKeys WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: UserId): RoomCrossSigningKeys?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomCrossSigningKeys)

    @Query("DELETE FROM CrossSigningKeys WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM CrossSigningKeys")
    suspend fun deleteAll()
}

internal class RoomCrossSigningKeysRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : CrossSigningKeysRepository {
    private val dao = db.crossSigningKeys()

    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString<Set<StoredCrossSigningKeys>>(entity.value) }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>) = withRoomWrite {
        dao.insert(
            RoomCrossSigningKeys(
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
