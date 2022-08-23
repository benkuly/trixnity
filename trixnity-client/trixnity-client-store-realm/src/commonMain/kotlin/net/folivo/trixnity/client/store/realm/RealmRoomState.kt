package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.events.Event

internal class RealmRoomState : RealmObject {
    var roomId: String = ""
    var type: String = ""
    var stateKey: String = ""
    var event: String = ""
}

internal class RealmRoomStateRepository(
    private val realm: Realm,
    private val json: Json,
) : RoomStateRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> {
        return realm.findByKey(key).associate {
            it.stateKey to json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun getBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? {
        return realm.findByKeys(firstKey, secondKey).find()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>) {
        value.entries.forEach { (secondKey, event) ->
            realm.write {
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
    }

    override suspend fun saveBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>) {
        realm.write {
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
    }

    override suspend fun delete(key: RoomStateRepositoryKey) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmRoomState>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: RoomStateRepositoryKey) =
        query<RealmRoomState>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun MutableRealm.findByKey(key: RoomStateRepositoryKey) =
        query<RealmRoomState>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun Realm.findByKeys(
        firstKey: RoomStateRepositoryKey,
        secondKey: String
    ) = query<RealmRoomState>(
        "roomId == $0 && type == $1 && stateKey == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()

    private fun MutableRealm.findByKeys(
        firstKey: RoomStateRepositoryKey,
        secondKey: String
    ) = query<RealmRoomState>(
        "roomId == $0 && type == $1 && stateKey == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()

}