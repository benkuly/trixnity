package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.coroutines.CoroutineContext


class SqlDelightRoomOutboxMessageRepository(
    private val db: RoomOutboxMessageQueries,
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
    private val context: CoroutineContext
) : RoomOutboxMessageRepository {
    private fun mapToRoomOutboxMessage(input: Sql_room_outbox_message): RoomOutboxMessage {
        val serializer = mappings.message.find { it.type == input.type }?.serializer
        requireNotNull(serializer)
        return RoomOutboxMessage(
            transactionId = input.transaction_id,
            roomId = RoomId(input.room_id),
            content = json.decodeFromString(serializer, input.content),
            sentAt = input.sent_at?.let { fromEpochMilliseconds(it) }
        )
    }

    override suspend fun getAll(): List<RoomOutboxMessage> = withContext(context) {
        db.getAllRoomOutboxMessages().executeAsList().map(::mapToRoomOutboxMessage)
    }

    override suspend fun get(key: String): RoomOutboxMessage? = withContext(context) {
        db.getRoomOutboxMessage(key).executeAsOneOrNull()?.let(::mapToRoomOutboxMessage)
    }

    override suspend fun save(key: String, value: RoomOutboxMessage) = withContext(context) {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        db.saveRoomOutboxMessage(
            @Suppress("UNCHECKED_CAST")
            Sql_room_outbox_message(
                transaction_id = key,
                room_id = value.roomId.full,
                type = mapping.type,
                content = json.encodeToString(mapping.serializer as KSerializer<MessageEventContent>, value.content),
                sent_at = value.sentAt?.toEpochMilliseconds()
            )
        )
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteRoomOutboxMessage(key)
    }

}