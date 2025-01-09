package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

@Entity(tableName = "Account")
data class RoomAccount(
    @PrimaryKey val id: Long = 0,
    val olmPickleKey: String? = null,
    val baseUrl: String? = null,
    val userId: UserId? = null,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val syncBatchToken: String? = null,
    val filterId: String? = null,
    val backgroundFilterId: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
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
) : AccountRepository {

    private val dao = db.account()

    override suspend fun get(key: Long): Account? = withRoomRead {
        dao.get(key)?.let { entity ->
            Account(
                olmPickleKey = entity.olmPickleKey ?: throw IllegalStateException("olmPickleKey not found"),
                baseUrl = entity.baseUrl ?: throw IllegalStateException("baseUrl not found"),
                userId = entity.userId ?: throw IllegalStateException("userId not found"),
                deviceId = entity.deviceId ?: throw IllegalStateException("deviceId not found"),
                accessToken = entity.accessToken,
                syncBatchToken = entity.syncBatchToken,
                filterId = entity.filterId,
                backgroundFilterId = entity.backgroundFilterId,
                displayName = entity.displayName,
                avatarUrl = entity.avatarUrl,
                isLocked = entity.isLocked,
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withRoomWrite {
        dao.insert(
            RoomAccount(
                id = key,
                olmPickleKey = value.olmPickleKey,
                baseUrl = value.baseUrl,
                userId = value.userId,
                deviceId = value.deviceId,
                accessToken = value.accessToken,
                syncBatchToken = value.syncBatchToken,
                filterId = value.filterId,
                backgroundFilterId = value.backgroundFilterId,
                displayName = value.displayName,
                avatarUrl = value.avatarUrl,
                isLocked = value.isLocked,
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
