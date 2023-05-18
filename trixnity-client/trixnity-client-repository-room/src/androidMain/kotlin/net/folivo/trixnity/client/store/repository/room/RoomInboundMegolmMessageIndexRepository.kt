package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

@Entity(
    tableName = "InboundMegolmMessageIndex",
    primaryKeys = ["sessionId", "roomId", "messageIndex"],
)
internal data class RoomInboundMegolmMessageIndex(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long,
    val eventId: EventId,
    val originTimestamp: Long,
)

@Dao
internal interface InboundMegolmMessageIndexDao {
    @Query(
        """
        SELECT * FROM InboundMegolmMessageIndex
        WHERE sessionId = :sessionId
        AND roomId = :roomId
        AND messageIndex = :messageIndex
        LIMIT 1
        """
    )
    suspend fun get(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
    ): RoomInboundMegolmMessageIndex?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomInboundMegolmMessageIndex)

    @Query(
        """
        DELETE FROM InboundMegolmMessageIndex
        WHERE sessionId = :sessionId
        AND roomId = :roomId
        AND messageIndex = :messageIndex
        """
    )
    suspend fun delete(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
    ): Int

    @Query("DELETE FROM InboundMegolmMessageIndex")
    suspend fun deleteAll()
}

internal class RoomInboundMegolmMessageIndexRepository(
    db: TrixnityRoomDatabase,
) : InboundMegolmMessageIndexRepository {
    private val dao = db.inboundMegolmMessageIndex()

    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? =
        dao.get(key.sessionId, key.roomId, key.messageIndex)
            ?.let { entity ->
                StoredInboundMegolmMessageIndex(
                    sessionId = entity.sessionId,
                    roomId = entity.roomId,
                    messageIndex = entity.messageIndex,
                    eventId = entity.eventId,
                    originTimestamp = entity.originTimestamp,
                )
            }

    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ) {
        dao.insert(
            RoomInboundMegolmMessageIndex(
                sessionId = key.sessionId,
                roomId = key.roomId,
                messageIndex = key.messageIndex,
                eventId = value.eventId,
                originTimestamp = value.originTimestamp,
            )
        )
    }

    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) {
        dao.delete(key.sessionId, key.roomId, key.messageIndex)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
