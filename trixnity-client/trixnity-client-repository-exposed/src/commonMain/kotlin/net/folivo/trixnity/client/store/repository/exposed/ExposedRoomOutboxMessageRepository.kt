package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomOutboxMessage : Table("room_outbox") {
    val transactionId = varchar("transaction_id", length = 255)
    override val primaryKey = PrimaryKey(transactionId)
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

    override suspend fun get(key: String): RoomOutboxMessage<*>? = withExposedRead {
        ExposedRoomOutboxMessage.select { ExposedRoomOutboxMessage.transactionId eq key }.firstOrNull()
            ?.let(::mapToRoomOutboxMessage)
    }

    override suspend fun save(key: String, value: RoomOutboxMessage<*>): Unit = withExposedWrite {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        ExposedRoomOutboxMessage.replace {
            it[transactionId] = key
            @Suppress("UNCHECKED_CAST")
            it[ExposedRoomOutboxMessage.value] = json.encodeToString(
                RoomOutboxMessage.serializer(mapping.serializer),
                value as RoomOutboxMessage<MessageEventContent>
            )
            it[contentType] = mapping.type
        }
    }

    override suspend fun delete(key: String): Unit = withExposedWrite {
        ExposedRoomOutboxMessage.deleteWhere { transactionId eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomOutboxMessage.deleteAll()
    }
}