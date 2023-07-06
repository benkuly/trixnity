package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

internal class RealmRoomState : RealmObject {
    @PrimaryKey
    var id: String = ""

    var roomId: String = ""
    var type: String = ""
    var stateKey: String = ""
    var event: String = ""
}

internal class RealmRoomStateRepository(
    private val json: Json,
) : RoomStateRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: RoomStateRepositoryKey): Map<String, Event<*>> = withRealmRead {
        findByKey(firstKey).copyFromRealm().associate {
            it.stateKey to json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmRoomState>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun get(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? =
        withRealmRead {
            findByKeys(firstKey, secondKey).find()?.copyFromRealm()?.let {
                json.decodeFromString(serializer, it.event)
            }
        }

    override suspend fun save(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>): Unit =
        withRealmWrite {
            copyToRealm(
                RealmRoomState().apply {
                    this.id = serializeKey(firstKey, secondKey)
                    this.roomId = firstKey.roomId.full
                    this.type = firstKey.type
                    this.stateKey = secondKey
                    this.event = json.encodeToString(serializer, value)
                },
                UpdatePolicy.ALL
            )
        }

    override suspend fun delete(firstKey: RoomStateRepositoryKey, secondKey: String) = withRealmWrite {
        val existing = findByKeys(firstKey, secondKey)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoomState>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: RoomStateRepositoryKey) =
        query<RealmRoomState>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun TypedRealm.findByKeys(
        firstKey: RoomStateRepositoryKey,
        secondKey: String
    ) = query<RealmRoomState>(
        "roomId == $0 && type == $1 && stateKey == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()
}