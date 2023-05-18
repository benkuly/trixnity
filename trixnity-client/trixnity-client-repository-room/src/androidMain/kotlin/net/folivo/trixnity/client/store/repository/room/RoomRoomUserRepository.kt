package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Entity(
    tableName = "RoomUser",
    primaryKeys = ["userId", "roomId"],
)
internal data class RoomRoomUser(
    val userId: UserId,
    val roomId: RoomId,
    val value: String,
)

@Dao
internal interface RoomUserDao {
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

    override suspend fun get(key: RoomId): Map<UserId, RoomUser> =
        dao.get(key)
            .associate { entity -> entity.userId to json.decodeFromString(entity.value) }

    override suspend fun getBySecondKey(firstKey: RoomId, secondKey: UserId): RoomUser? =
        dao.get(secondKey, firstKey)
            ?.let { entity -> json.decodeFromString(entity.value) }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) {
        dao.insertAll(
            value.map { (userId, roomUser) ->
                RoomRoomUser(
                    userId = userId,
                    roomId = key,
                    value = json.encodeToString(roomUser),
                )
            }
        )
    }

    override suspend fun saveBySecondKey(firstKey: RoomId, secondKey: UserId, value: RoomUser) {
        dao.insert(
            RoomRoomUser(
                userId = secondKey,
                roomId = firstKey,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: RoomId) {
        dao.delete(key)
    }

    override suspend fun deleteBySecondKey(firstKey: RoomId, secondKey: UserId) {
        dao.delete(firstKey, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
