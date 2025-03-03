package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredOlmSession

@Entity(tableName = "OlmSession")
data class RoomOlmSession(
    @PrimaryKey val senderKey: String,
    val value: String,
)

@Dao
interface OlmSessionDao {
    @Query("SELECT * FROM OlmSession WHERE senderKey = :senderKey LIMIT 1")
    suspend fun get(senderKey: String): RoomOlmSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOlmSession)

    @Query("DELETE FROM OlmSession WHERE senderKey = :senderKey")
    suspend fun delete(senderKey: String)

    @Query("DELETE FROM OlmSession")
    suspend fun deleteAll()
}

internal class RoomOlmSessionRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : OlmSessionRepository {
    private val dao = db.olmSession()

    override suspend fun get(key: Curve25519KeyValue): Set<StoredOlmSession>? = withRoomRead {
        dao.get(key.value)
            ?.let { entity -> json.decodeFromString(entity.value) }
    }

    override suspend fun save(key: Curve25519KeyValue, value: Set<StoredOlmSession>) = withRoomWrite {
        dao.insert(
            RoomOlmSession(
                senderKey = key.value,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: Curve25519KeyValue) = withRoomWrite {
        dao.delete(key.value)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
