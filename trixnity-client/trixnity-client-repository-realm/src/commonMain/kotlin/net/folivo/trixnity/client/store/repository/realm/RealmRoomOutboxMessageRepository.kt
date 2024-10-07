package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal class RealmRoomOutboxMessage2 : RealmObject {
    @PrimaryKey
    var id: String = ""

    var roomId: String = ""
    var transactionId: String = ""
    var value: String = ""
    var contentType: String = ""
}

internal class RealmRoomOutboxMessageRepository(
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository {
    override suspend fun getAll(): List<RoomOutboxMessage<*>> = withRealmRead {
        query<RealmRoomOutboxMessage2>().find().copyFromRealm().map { it.mapToRoomOutboxMessage() }
    }

    override suspend fun get(key: RoomOutboxMessageRepositoryKey): RoomOutboxMessage<*>? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.mapToRoomOutboxMessage()
    }

    override suspend fun save(key: RoomOutboxMessageRepositoryKey, value: RoomOutboxMessage<*>): Unit = withRealmWrite {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)
        copyToRealm(
            RealmRoomOutboxMessage2().apply {
                id = serializeKey(key)
                roomId = key.roomId.full
                transactionId = key.transactionId
                @Suppress("UNCHECKED_CAST")
                this.value = json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer),
                    value as RoomOutboxMessage<MessageEventContent>,
                )
                contentType = mapping.type
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: RoomOutboxMessageRepositoryKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoomOutboxMessage2>().find()
        delete(existing)
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmRoomOutboxMessage2>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    private fun RealmRoomOutboxMessage2.mapToRoomOutboxMessage(): RoomOutboxMessage<out MessageEventContent> {
        val serializer = mappings.message.find { it.type == this.contentType }?.serializer
        requireNotNull(serializer)
        return json.decodeFromString(RoomOutboxMessage.serializer(serializer), this.value)
    }

    private fun TypedRealm.findByKey(key: RoomOutboxMessageRepositoryKey) =
        query<RealmRoomOutboxMessage2>(
            "roomId == $0 && transactionId == $1",
            key.roomId.full,
            key.transactionId
        ).first()
}