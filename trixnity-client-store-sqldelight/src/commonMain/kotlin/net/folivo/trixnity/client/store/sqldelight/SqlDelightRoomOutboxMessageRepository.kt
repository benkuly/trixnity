package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.coroutines.CoroutineContext


class SqlDelightRoomOutboxMessageRepository(
    private val db: RoomOutboxMessageQueries,
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
    private val context: CoroutineContext
) : RoomOutboxMessageRepository {
    private fun mapToRoomOutboxMessage(input: Sql_room_outbox_message): RoomOutboxMessage<*> {
        val serializer = mappings.message.find { it.type == input.content_type }?.serializer
        requireNotNull(serializer)
        return json.decodeFromString(RoomOutboxMessage.serializer(serializer), input.value_)
    }

    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withContext(context) {
        db.getAllRoomOutboxMessages().executeAsList().map(::mapToRoomOutboxMessage)
    }

    override suspend fun get(key: String): RoomOutboxMessage<*>? = withContext(context) {
        db.getRoomOutboxMessage(key).executeAsOneOrNull()?.let(::mapToRoomOutboxMessage)
    }

    override suspend fun save(key: String, value: RoomOutboxMessage<*>) = withContext(context) {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        db.saveRoomOutboxMessage(
            @Suppress("UNCHECKED_CAST")
            Sql_room_outbox_message(
                transaction_id = key,
                value_ = json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer as KSerializer<MessageEventContent>),
                    value as RoomOutboxMessage<MessageEventContent>
                ),
                content_type = mapping.type,
            )
        )
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteRoomOutboxMessage(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllRoomOutboxMessages()
    }
}