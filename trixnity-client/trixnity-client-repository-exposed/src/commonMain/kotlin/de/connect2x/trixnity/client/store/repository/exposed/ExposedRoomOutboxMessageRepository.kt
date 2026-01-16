package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomOutboxMessage : Table("room_outbox_2") {
    val transactionId = varchar("transaction_id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(roomId, transactionId)
    val value = text("value")
    val contentType = text("content_type")
}

internal class ExposedRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository {
    private fun mapToRoomOutboxMessage(input: ResultRow): RoomOutboxMessage<*> {
        val serializer = mappings.message.find { it.type == input[ExposedRoomOutboxMessage.contentType] }?.serializer
        requireNotNull(serializer)
        return json.decodeFromString(RoomOutboxMessage.serializer(serializer), input[ExposedRoomOutboxMessage.value])
    }

    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withExposedRead {
        ExposedRoomOutboxMessage.selectAll().map(::mapToRoomOutboxMessage)
    }

    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? = withExposedRead {
        ExposedRoomOutboxMessage.selectAll().where {
            ExposedRoomOutboxMessage.roomId.eq(key.roomId.full) and ExposedRoomOutboxMessage.transactionId.eq(key.transactionId)
        }.firstOrNull()
            ?.let(::mapToRoomOutboxMessage)
    }

    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>) = withExposedWrite {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        ExposedRoomOutboxMessage.upsert {
            it[roomId] = key.roomId.full
            it[transactionId] = key.transactionId
            @Suppress("UNCHECKED_CAST")
            it[ExposedRoomOutboxMessage.value] = json.encodeToString(
                RoomOutboxMessage.serializer(mapping.serializer),
                value as RoomOutboxMessage<MessageEventContent>
            )
            it[contentType] = mapping.type
        }
    }

    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) = withExposedWrite {
        ExposedRoomOutboxMessage.deleteWhere { roomId.eq(key.roomId.full) and transactionId.eq(key.transactionId) }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomOutboxMessage.deleteAll()
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomOutboxMessage.deleteWhere { ExposedRoomOutboxMessage.roomId.eq(roomId.full) }
    }
}