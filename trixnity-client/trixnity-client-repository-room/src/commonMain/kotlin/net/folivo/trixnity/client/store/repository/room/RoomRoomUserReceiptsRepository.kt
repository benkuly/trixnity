package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.repository.RoomUserReceiptsRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Entity(
    tableName = "RoomUserReceipts",
    primaryKeys = ["userId", "roomId"],
)
data class RoomRoomUserReceipts(
    val userId: UserId,
    val roomId: RoomId,
    val value: String,
)

@Dao
interface RoomUserReceiptsDao {
    @Query("SELECT * FROM RoomUserReceipts WHERE userId = :userId AND roomId = :roomId LIMIT 1")
    suspend fun get(userId: UserId, roomId: RoomId): RoomRoomUserReceipts?

    @Query("SELECT * FROM RoomUserReceipts WHERE roomId = :roomId")
    suspend fun get(roomId: RoomId): List<RoomRoomUserReceipts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomUserReceipts)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoomRoomUserReceipts>)

    @Query("DELETE FROM RoomUserReceipts WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomUserReceipts WHERE roomId = :roomId AND userId = :userId")
    suspend fun delete(roomId: RoomId, userId: UserId)

    @Query("DELETE FROM RoomUserReceipts")
    suspend fun deleteAll()
}

internal class RoomRoomUserReceiptsRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomUserReceiptsRepository {
    private val dao = db.roomUserReceipts()

    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUserReceipts> = withRoomRead {
        dao.get(firstKey)
            .associate { entity -> entity.userId to json.decodeFromString(entity.value) }
    }

    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUserReceipts? = withRoomRead {
        dao.get(secondKey, firstKey)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUserReceipts) = withRoomWrite {
        dao.insert(
            RoomRoomUserReceipts(
                userId = secondKey,
                roomId = firstKey,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun deleteByRoomId(key: RoomId) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun delete(firstKey: RoomId, secondKey: UserId) = withRoomWrite {
        dao.delete(firstKey, secondKey)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
