package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

@Entity(tableName = "Account")
internal data class RoomAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
)

@Dao
internal interface AccountDao {
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

    override suspend fun get(key: Long): Account? =
        dao.get(key)?.let { entity ->
            Account(
                olmPickleKey = entity.olmPickleKey,
                baseUrl = entity.baseUrl,
                userId = entity.userId,
                deviceId = entity.deviceId,
                accessToken = entity.accessToken,
                syncBatchToken = entity.syncBatchToken,
                filterId = entity.filterId,
                backgroundFilterId = entity.backgroundFilterId,
                displayName = entity.displayName,
                avatarUrl = entity.avatarUrl,
            )
        }

    override suspend fun save(key: Long, value: Account) {
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
            )
        )
    }

    override suspend fun delete(key: Long) {
        dao.delete(key)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
