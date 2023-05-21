package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

@Entity(
    tableName = "KeyVerificationState",
    primaryKeys = ["keyId", "keyAlgorithm"]
)
internal data class RoomKeyVerificationState(
    val keyId: String,
    val keyAlgorithm: KeyAlgorithm,
    val verificationState: String,
)

@Dao
internal interface KeyVerificationStateDao {
    @Query("SELECT * FROM KeyVerificationState WHERE keyId = :keyId AND keyAlgorithm = :keyAlgorithm LIMIT 1")
    suspend fun get(keyId: String, keyAlgorithm: KeyAlgorithm): RoomKeyVerificationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomKeyVerificationState)

    @Query("DELETE FROM KeyVerificationState WHERE keyId = :keyId AND keyAlgorithm = :keyAlgorithm")
    suspend fun delete(keyId: String, keyAlgorithm: KeyAlgorithm)

    @Query("DELETE FROM KeyVerificationState")
    suspend fun deleteAll()
}

internal class RoomKeyVerificationStateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : KeyVerificationStateRepository {
    private val dao = db.keyVerificationState()

    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? =
        dao.get(key.keyId, key.keyAlgorithm)
            ?.let { json.decodeFromString(it.verificationState) }

    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState) {
        dao.insert(
            RoomKeyVerificationState(
                keyId = key.keyId,
                keyAlgorithm = key.keyAlgorithm,
                verificationState = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: KeyVerificationStateKey) {
        dao.delete(key.keyId, key.keyAlgorithm)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
