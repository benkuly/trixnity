package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository

@Entity(tableName = "SecretKeyRequest")
data class RoomSecretKeyRequest(
    @PrimaryKey val id: String,
    val value: String,
)

@Dao
interface SecretKeyRequestDao {
    @Query("SELECT * FROM SecretKeyRequest WHERE id = :id LIMIT 1")
    suspend fun get(id: String): RoomSecretKeyRequest?

    @Query("SELECT * FROM SecretKeyRequest")
    suspend fun getAll(): List<RoomSecretKeyRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomSecretKeyRequest)

    @Query("DELETE FROM SecretKeyRequest WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM SecretKeyRequest")
    suspend fun deleteAll()
}

internal class RoomSecretKeyRequestRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : SecretKeyRequestRepository {

    private val dao = db.secretKeyRequest()

    override suspend fun get(key: String): StoredSecretKeyRequest? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    override suspend fun getAll(): List<StoredSecretKeyRequest> =
        dao.getAll()
            .map { entity -> json.decodeFromString(entity.value) }

    override suspend fun save(key: String, value: StoredSecretKeyRequest) {
        dao.insert(
            RoomSecretKeyRequest(
                id = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: String) {
        dao.delete(key)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
