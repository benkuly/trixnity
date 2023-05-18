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
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.crypto.SecretType

@Entity(tableName = "Secrets")
internal data class RoomSecrets(
    @PrimaryKey val id: Long,
    val value: String,
)

@Dao
internal interface SecretsDao {
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

    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) {
        dao.insert(
            RoomSecrets(
                id = key,
                value = json.encodeToString(value),
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
