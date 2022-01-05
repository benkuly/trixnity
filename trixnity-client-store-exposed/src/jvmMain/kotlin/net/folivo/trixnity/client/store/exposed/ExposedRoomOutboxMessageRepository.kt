package net.folivo.trixnity.client.store.exposed

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.jetbrains.exposed.sql.*

internal object ExposedRoomOutboxMessage : Table("room_outbox") {
    val transactionId = varchar("transaction_id", length = 65535)
    override val primaryKey = PrimaryKey(transactionId)
    val roomId = text("room_id")
    val type = text("type")
    val content = text("content")
    val sentAt = long("sent_at").nullable()
}

internal class ExposedRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository {
    private fun mapToRoomOutboxMessage(input: ResultRow): RoomOutboxMessage {
        val serializer = mappings.message.find { it.type == input[ExposedRoomOutboxMessage.type] }?.serializer
        requireNotNull(serializer)
        return RoomOutboxMessage(
            transactionId = input[ExposedRoomOutboxMessage.transactionId],
            roomId = RoomId(input[ExposedRoomOutboxMessage.roomId]),
            content = json.decodeFromString(serializer, input[ExposedRoomOutboxMessage.content]),
            sentAt = input[ExposedRoomOutboxMessage.sentAt]?.let { Instant.fromEpochMilliseconds(it) }
        )
    }

    override suspend fun getAll(): List<RoomOutboxMessage> {
        return ExposedRoomOutboxMessage.selectAll().map(::mapToRoomOutboxMessage)
    }

    override suspend fun get(key: String): RoomOutboxMessage? {
        return ExposedRoomOutboxMessage.select { ExposedRoomOutboxMessage.transactionId eq key }.firstOrNull()
            ?.let(::mapToRoomOutboxMessage)
    }

    override suspend fun save(key: String, value: RoomOutboxMessage) {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        ExposedRoomOutboxMessage.replace {
            it[transactionId] = key
            it[roomId] = value.roomId.full
            it[type] = mapping.type
            @Suppress("UNCHECKED_CAST")
            it[content] = json.encodeToString(mapping.serializer as KSerializer<MessageEventContent>, value.content)
            it[sentAt] = value.sentAt?.toEpochMilliseconds()
        }
    }

    override suspend fun delete(key: String) {
        ExposedRoomOutboxMessage.deleteWhere { ExposedRoomOutboxMessage.transactionId eq key }
    }

}