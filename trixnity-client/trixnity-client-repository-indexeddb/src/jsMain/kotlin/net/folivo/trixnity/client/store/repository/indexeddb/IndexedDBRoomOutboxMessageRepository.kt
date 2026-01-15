package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

@Serializable
internal class IndexedDBRoomOutboxMessage<T : MessageEventContent>(
    val roomId: RoomId,
    val value: RoomOutboxMessage<T>,
    val contentType: String,
)

internal class IndexedDBRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository, IndexedDBRepository(objectStoreName) {

    private val serializer = object : KSerializer<IndexedDBRoomOutboxMessage<*>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IndexedDBRoomOutboxMessage")

        override fun deserialize(decoder: Decoder): IndexedDBRoomOutboxMessage<*> {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val contentType = jsonObject["contentType"]?.jsonPrimitive?.content
            val serializer = mappings.message.find { it.type == contentType }?.serializer
            checkNotNull(serializer)
            return json.decodeFromJsonElement(IndexedDBRoomOutboxMessage.serializer(serializer), jsonObject)
        }

        override fun serialize(encoder: Encoder, value: IndexedDBRoomOutboxMessage<*>) {
            require(encoder is JsonEncoder)
            val serializer = mappings.message.find { it.type == value.contentType }?.serializer
            checkNotNull(serializer)
            encoder.encodeJsonElement(
                @Suppress("UNCHECKED_CAST")
                encoder.json.encodeToJsonElement(
                    IndexedDBRoomOutboxMessage.serializer(serializer),
                    value as IndexedDBRoomOutboxMessage<MessageEventContent>
                ),
            )
        }
    }

    private val internalRepository =
        object : IndexedDBFullRepository<RoomOutboxMessageRepositoryKey, IndexedDBRoomOutboxMessage<*>>(
            objectStoreName = objectStoreName,
            keySerializer = { arrayOf(it.roomId.full, it.transactionId) },
            valueSerializer = serializer,
            json = json
        ) {
            override fun serializeKey(key: RoomOutboxMessageRepositoryKey): String =
                this@IndexedDBRoomOutboxMessageRepository.serializeKey(key)
        }

    companion object {
        const val objectStoreName = "room_outbox_message_2"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 6)
                createIndexedDBMinimalStoreRepository(database, objectStoreName) {
                    createIndex("roomId", KeyPath("roomId"), unique = false)
                }
        }
    }


    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? =
        internalRepository.get(key)?.value

    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withIndexedDBRead { store ->
        store.openCursor(autoContinue = true)
            .mapNotNull { json.decodeFromDynamicNullable(serializer, it.value) }
            .map { it.value }
            .toList()
    }

    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>) {
        val contentType = mappings.message.find { it.kClass.isInstance(value.content) }?.type
        checkNotNull(contentType)
        internalRepository.save(key, IndexedDBRoomOutboxMessage(key.roomId, value, contentType))
    }

    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) =
        internalRepository.delete(key)

    override suspend fun deleteAll() =
        internalRepository.deleteAll()

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(Key(roomId.full), autoContinue = true)
            .collect {
                store.delete(Key(it.primaryKey))
            }
    }
}