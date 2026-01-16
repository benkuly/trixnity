package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.repository.AccountRepository
import de.connect2x.trixnity.core.model.UserId

@Entity(tableName = "Account")
data class RoomAccount(
    @PrimaryKey val id: Long = 0,
    val olmPickleKey: String? = null,
    val baseUrl: String? = null,
    val userId: UserId? = null,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val syncBatchToken: String? = null,
    val filterId: String? = null,
    val backgroundFilterId: String? = null,
    val profile: String? = null,
    val isLocked: Boolean = false,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM Account WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomAccount)

    @Query("DELETE FROM Account WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Account")
    suspend fun deleteAll()
}

internal class RoomAccountRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : AccountRepository {

    private val dao = db.account()

    override suspend fun get(key: Long): Account? = withRoomRead {
        dao.get(key)?.let { entity ->
            Account(
                olmPickleKey = entity.olmPickleKey,
                baseUrl = entity.baseUrl,
                userId = entity.userId ?: throw IllegalStateException("userId not found"),
                deviceId = entity.deviceId ?: throw IllegalStateException("deviceId not found"),
                accessToken = entity.accessToken,
                refreshToken = entity.refreshToken,
                syncBatchToken = entity.syncBatchToken,
                filterId = entity.filterId,
                backgroundFilterId = entity.backgroundFilterId,
                profile = entity.profile?.let { json.decodeFromString(it) },
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withRoomWrite {
        dao.insert(
            @Suppress("DEPRECATION")
            RoomAccount(
                id = key,
                olmPickleKey = value.olmPickleKey,
                baseUrl = value.baseUrl,
                userId = value.userId,
                deviceId = value.deviceId,
                accessToken = value.accessToken,
                refreshToken = value.refreshToken,
                syncBatchToken = value.syncBatchToken,
                filterId = value.filterId,
                backgroundFilterId = value.backgroundFilterId,
                profile = value.profile?.let { json.encodeToString(it) },
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
