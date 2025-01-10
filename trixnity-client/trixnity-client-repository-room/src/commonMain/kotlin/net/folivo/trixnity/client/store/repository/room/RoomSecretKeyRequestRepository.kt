package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
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

    override suspend fun get(key: String): StoredSecretKeyRequest? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun getAll(): List<StoredSecretKeyRequest> = withRoomRead {
        dao.getAll()
            .map { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: String, value: StoredSecretKeyRequest) = withRoomWrite {
        dao.insert(
            RoomSecretKeyRequest(
                id = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: String) = withRoomWrite {
        dao.delete(key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
