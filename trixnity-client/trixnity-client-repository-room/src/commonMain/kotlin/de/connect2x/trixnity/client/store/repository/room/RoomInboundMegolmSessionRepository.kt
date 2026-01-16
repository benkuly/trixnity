package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession

@Entity(
    tableName = "InboundMegolmSession",
    primaryKeys = ["senderKey", "sessionId", "roomId"],
)
data class RoomInboundMegolmSession(
    val senderKey: String,
    val sessionId: String,
    val roomId: RoomId,
    val firstKnownIndex: Long,
    val hasBeenBackedUp: Boolean,
    val isTrusted: Boolean,
    val senderSigningKey: String,
    val forwardingCurve25519KeyChain: String,
    val pickled: String,
)

@Dao
interface InboundMegolmSessionDao {
    @Query("SELECT * FROM InboundMegolmSession WHERE sessionId = :sessionId AND roomId = :roomId LIMIT 1")
    suspend fun get(sessionId: String, roomId: RoomId): RoomInboundMegolmSession?

    @Query("SELECT * FROM InboundMegolmSession")
    suspend fun getAll(): List<RoomInboundMegolmSession>

    @Query("SELECT * FROM InboundMegolmSession WHERE hasBeenBackedUp = 0")
    suspend fun getNotBackedUp(): List<RoomInboundMegolmSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomInboundMegolmSession)

    @Query("DELETE FROM InboundMegolmSession WHERE sessionId = :sessionId AND roomId = :roomId")
    suspend fun delete(sessionId: String, roomId: RoomId)

    @Query("DELETE FROM InboundMegolmSession")
    suspend fun deleteAll()
}

internal class RoomInboundMegolmSessionRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : InboundMegolmSessionRepository {
    private val dao = db.inboundMegolmSession()

    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? = withRoomRead {
        dao.get(key.sessionId, key.roomId)
            ?.toModel()
    }

    override suspend fun getAll(): List<StoredInboundMegolmSession> = withRoomRead {
        dao.getAll().map { it.toModel() }
    }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withRoomRead {
        dao.getNotBackedUp()
            .map { entity -> entity.toModel() }
            .toSet()
    }

    override suspend fun save(
        key: InboundMegolmSessionRepositoryKey,
        value: StoredInboundMegolmSession
    ) = withRoomWrite {
        dao.insert(
            RoomInboundMegolmSession(
                senderKey = value.senderKey.value,
                sessionId = key.sessionId,
                roomId = key.roomId,
                firstKnownIndex = value.firstKnownIndex,
                hasBeenBackedUp = value.hasBeenBackedUp,
                isTrusted = value.isTrusted,
                senderSigningKey = value.senderSigningKey.value,
                forwardingCurve25519KeyChain = json.encodeToString(value.forwardingCurve25519KeyChain),
                pickled = value.pickled,
            )
        )
    }

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) = withRoomWrite {
        dao.delete(key.sessionId, key.roomId)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }

    private fun RoomInboundMegolmSession.toModel(): StoredInboundMegolmSession =
        StoredInboundMegolmSession(
            senderKey = Curve25519KeyValue(senderKey),
            sessionId = sessionId,
            roomId = roomId,
            firstKnownIndex = firstKnownIndex,
            hasBeenBackedUp = hasBeenBackedUp,
            isTrusted = isTrusted,
            senderSigningKey = KeyValue.Ed25519KeyValue(senderSigningKey),
            forwardingCurve25519KeyChain = json.decodeFromString(forwardingCurve25519KeyChain),
            pickled = pickled
        )
}
