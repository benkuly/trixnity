package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.SerializerMapping

@Entity(tableName = "RoomOutboxMessage")
internal data class RoomRoomOutboxMessage(
    @PrimaryKey val transactionId: String,
    val value: String,
    val contentType: String,
)

@Dao
internal interface RoomOutboxMessageDao {
    @Query("SELECT * FROM RoomOutboxMessage WHERE transactionId = :transactionId LIMIT 1")
    suspend fun get(transactionId: String): RoomRoomOutboxMessage?

    @Query("SELECT * FROM RoomOutboxMessage")
    suspend fun getAll(): List<RoomRoomOutboxMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomOutboxMessage)

    @Query("DELETE FROM RoomOutboxMessage WHERE transactionId = :transactionId")
    suspend fun delete(transactionId: String)

    @Query("DELETE FROM RoomOutboxMessage")
    suspend fun deleteAll()
}

internal class RoomRoomOutboxMessageRepository(
    db: TrixnityRoomDatabase,
    private val mappings: EventContentSerializerMappings,
    private val json: Json,
) : RoomOutboxMessageRepository {
    private val dao = db.roomOutboxMessage()

    override suspend fun get(key: String): RoomOutboxMessage<*>? =
        dao.get(key)?.toModel()

    override suspend fun getAll(): List<RoomOutboxMessage<*>> =
        dao.getAll().map { it.toModel() }

    override suspend fun save(key: String, value: RoomOutboxMessage<*>) {
        val mapping = getMappingOrThrow { it.kClass.isInstance(value.content) }
        @Suppress("UNCHECKED_CAST")
        dao.insert(
            RoomRoomOutboxMessage(
                transactionId = key,
                value = json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer as KSerializer<MessageEventContent>),
                    value as RoomOutboxMessage<MessageEventContent>,
                ),
                contentType = mapping.type,
            )
        )
    }

    override suspend fun delete(key: String) {
        dao.delete(key)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun RoomRoomOutboxMessage.toModel(): RoomOutboxMessage<*> =
        json.decodeFromString(
            RoomOutboxMessage.serializer(
                getMappingOrThrow { it.type == contentType }.serializer,
            ),
            value,
        )

    private fun getMappingOrThrow(
        condition: (SerializerMapping<out MessageEventContent>) -> Boolean,
    ): SerializerMapping<out MessageEventContent> =
        mappings.message
            .find { condition.invoke(it) }
            ?: error("No serialiser found required condition!")
}
