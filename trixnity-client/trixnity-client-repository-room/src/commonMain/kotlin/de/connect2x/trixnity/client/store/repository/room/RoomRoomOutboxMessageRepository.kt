package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMapping
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

@Entity(
    tableName = "RoomOutboxMessage2",
    primaryKeys = ["roomId", "transactionId"]
)
data class RoomRoomOutboxMessage(
    val roomId: RoomId,
    val transactionId: String,
    val value: String,
    val contentType: String,
)

@Dao
interface RoomOutboxMessageDao {
    @Query("SELECT * FROM RoomOutboxMessage2 WHERE roomId = :roomId AND transactionId = :transactionId LIMIT 1")
    suspend fun get(roomId: RoomId, transactionId: String): RoomRoomOutboxMessage?

    @Query("SELECT * FROM RoomOutboxMessage2")
    suspend fun getAll(): List<RoomRoomOutboxMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomOutboxMessage)

    @Query("DELETE FROM RoomOutboxMessage2 WHERE roomId = :roomId AND transactionId = :transactionId")
    suspend fun delete(roomId: RoomId, transactionId: String)

    @Query("DELETE FROM RoomOutboxMessage2 WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomOutboxMessage2")
    suspend fun deleteAll()
}

internal class RoomRoomOutboxMessageRepository(
    db: TrixnityRoomDatabase,
    private val mappings: EventContentSerializerMappings,
    private val json: Json,
) : RoomOutboxMessageRepository {
    private val dao = db.roomOutboxMessage()

    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? = withRoomRead {
        dao.get(key.roomId, key.transactionId)?.toModel()
    }

    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withRoomRead {
        dao.getAll().map { it.toModel() }
    }

    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>) = withRoomWrite {
        val mapping = getMappingOrThrow { it.kClass.isInstance(value.content) }
        @Suppress("UNCHECKED_CAST")
        dao.insert(
            RoomRoomOutboxMessage(
                roomId = key.roomId,
                transactionId = key.transactionId,
                value = json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer as KSerializer<MessageEventContent>),
                    value as RoomOutboxMessage<MessageEventContent>,
                ),
                contentType = mapping.type,
            )
        )
    }

    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) = withRoomWrite {
        dao.delete(key.roomId, key.transactionId)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRoomWrite {
        dao.delete(roomId)
    }

    private fun RoomRoomOutboxMessage.toModel(): RoomOutboxMessage<*> =
        json.decodeFromString(
            RoomOutboxMessage.serializer(
                getMappingOrThrow { it.type == contentType }.serializer,
            ),
            value,
        )

    private fun getMappingOrThrow(
        condition: (EventContentSerializerMapping<out MessageEventContent>) -> Boolean,
    ): EventContentSerializerMapping<out MessageEventContent> =
        mappings.message
            .find { condition.invoke(it) }
            ?: error("No serialiser found required condition!")
}
