package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Entity(
    tableName = "RoomUser",
    primaryKeys = ["userId", "roomId"],
)
data class RoomRoomUser(
    val userId: UserId,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface RoomUserDao {
    @Query("SELECT * FROM RoomUser WHERE userId = :userId AND roomId = :roomId LIMIT 1")
    suspend fun get(userId: UserId, roomId: RoomId): RoomRoomUser?

    @Query("SELECT * FROM RoomUser WHERE roomId = :roomId")
    suspend fun get(roomId: RoomId): List<RoomRoomUser>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoomRoomUser>)

    @Query("DELETE FROM RoomUser WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomUser WHERE roomId = :roomId AND userId = :userId")
    suspend fun delete(roomId: RoomId, userId: UserId)

    @Query("DELETE FROM RoomUser")
    suspend fun deleteAll()
}

internal class RoomRoomUserRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomUserRepository {
    private val dao = db.roomUser()

    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUser> = withRoomRead {
        dao.get(firstKey)
            .associate { entity -> entity.userId to json.decodeFromString(entity.value) }
    }

    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUser? = withRoomRead {
        dao.get(secondKey, firstKey)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUser) = withRoomWrite {
        dao.insert(
            RoomRoomUser(
                userId = secondKey,
                roomId = firstKey,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRoomWrite {
        dao.delete(roomId)
    }

    override suspend fun delete(firstKey: RoomId, secondKey: UserId) = withRoomWrite {
        dao.delete(firstKey, secondKey)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
