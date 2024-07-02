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
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

@Entity(tableName = "OutboundMegolmSession")
data class RoomOutboundMegolmSession(
    @PrimaryKey val roomId: RoomId,
    val value: String,
)

@Dao
interface OutboundMegolmSessionDao {
    @Query("SELECT * FROM OutboundMegolmSession WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: RoomId): RoomOutboundMegolmSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomOutboundMegolmSession)

    @Query("DELETE FROM OutboundMegolmSession WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM OutboundMegolmSession")
    suspend fun deleteAll()
}

internal class RoomOutboundMegolmSessionRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : OutboundMegolmSessionRepository {
    private val dao = db.outboundMegolmSession()

    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? =
        dao.get(key)
            ?.let { entity -> json.decodeFromString(entity.value) }

    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) {
        dao.insert(
            RoomOutboundMegolmSession(
                roomId = key,
                value = json.encodeToString(value),
            )
        )
    }

    override suspend fun delete(key: RoomId) {
        dao.delete(key)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
