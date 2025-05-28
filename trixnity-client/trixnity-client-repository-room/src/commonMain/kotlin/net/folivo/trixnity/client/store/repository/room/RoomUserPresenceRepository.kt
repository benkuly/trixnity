package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.store.repository.UserPresenceRepository
import net.folivo.trixnity.core.model.UserId

@Entity(
    tableName = "UserPresence",
    primaryKeys = ["userId"],
)
data class RoomUserPresence(
    val userId: UserId,
    val value: String,
)

@Dao
interface UserPresenceDao {
    @Query("SELECT * FROM UserPresence WHERE userId = :userId  LIMIT 1")
    suspend fun get(userId: UserId): RoomUserPresence?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomUserPresence)

    @Query("DELETE FROM UserPresence WHERE userId = :userId")
    suspend fun delete(userId: UserId)

    @Query("DELETE FROM UserPresence")
    suspend fun deleteAll()
}

internal class RoomUserPresenceRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : UserPresenceRepository {
    private val dao = db.userPresence()

    override suspend fun get(key: UserId): UserPresence? = withRoomRead {
        dao.get(key)?.let { json.decodeFromString(it.value) }
    }

    override suspend fun save(
        key: UserId,
        value: UserPresence
    ) = withRoomWrite {
        dao.insert(
            RoomUserPresence(
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
