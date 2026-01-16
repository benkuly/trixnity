package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.crypto.SecretType

@Entity(tableName = "Secrets")
data class RoomSecrets(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
interface SecretsDao {
    @Query("SELECT * FROM Secrets WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): RoomSecrets?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomSecrets)

    @Query("DELETE FROM Secrets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Secrets")
    suspend fun deleteAll()
}

internal class RoomSecretsRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : SecretsRepository {

    private val dao = db.secrets()

    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? = withRoomRead {
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) = withRoomWrite {
        dao.insert(
            RoomSecrets(
                id = key,
                value = json.encodeToString(value),
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
