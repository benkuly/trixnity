package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal class RealmRoomOutboxMessage : RealmObject {
    @PrimaryKey
    var transactionId: String = ""
    var value: String = ""
    var contentType: String = ""
}

internal class RealmRoomOutboxMessageRepository(
    private val realm: Realm,
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
) : RoomOutboxMessageRepository {
    override suspend fun getAll(): List<RoomOutboxMessage<*>> {
        return realm.query<RealmRoomOutboxMessage>().find().map { it.mapToRoomOutboxMessage() }
    }

    override suspend fun get(key: String): RoomOutboxMessage<*>? {
        return realm.findByKey(key).find()?.mapToRoomOutboxMessage()
    }

    override suspend fun save(key: String, value: RoomOutboxMessage<*>) {
        val mapping = mappings.message.find { it.kClass.isInstance(value.content) }
        requireNotNull(mapping)

        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmRoomOutboxMessage().apply {
                transactionId = value.transactionId
            }).apply {
                @Suppress("UNCHECKED_CAST")
                this.value = json.encodeToString(
                    RoomOutboxMessage.serializer(mapping.serializer as KSerializer<MessageEventContent>),
                    value as RoomOutboxMessage<MessageEventContent>,
                )
                contentType = mapping.type
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: String) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmRoomOutboxMessage>().find()
            delete(existing)
        }
    }

    private fun RealmRoomOutboxMessage.mapToRoomOutboxMessage(): RoomOutboxMessage<out MessageEventContent> {
        val serializer = mappings.message.find { it.type == this.contentType }?.serializer
        requireNotNull(serializer)
        return json.decodeFromString(RoomOutboxMessage.serializer(serializer), this.value)
    }

    private fun Realm.findByKey(key: String) = query<RealmRoomOutboxMessage>("transactionId == $0", key).first()
    private fun MutableRealm.findByKey(key: String) = query<RealmRoomOutboxMessage>("transactionId == $0", key).first()

}