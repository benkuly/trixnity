package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

internal class RealmRoomState : RealmObject {
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

    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> = withRealmRead {
        findByKey(key).associate {
            it.stateKey to json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmRoomState>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun getBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? =
        withRealmRead {
            findByKeys(firstKey, secondKey).find()?.let {
                json.decodeFromString(serializer, it.event)
            }
        }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>) = withRealmWrite {
        value.entries.forEach { (secondKey, event) ->
            val existing = findByKeys(key, secondKey).find()
            val upsert = (existing ?: RealmRoomState()).apply {
                this.roomId = key.roomId.full
                this.type = key.type
                this.stateKey = secondKey
                this.event = json.encodeToString(serializer, event)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun saveBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>) =
        withRealmWrite {
            val existing = findByKeys(firstKey, secondKey).find()
            val upsert = (existing ?: RealmRoomState()).apply {
                this.roomId = firstKey.roomId.full
                this.type = firstKey.type
                this.stateKey = secondKey
                this.event = json.encodeToString(serializer, value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }

    override suspend fun delete(key: RoomStateRepositoryKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String) = withRealmWrite {
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