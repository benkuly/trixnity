package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.events.Event

internal class RealmRoomAccountData : RealmObject {
    var roomId: String = ""
    var type: String = ""
    var key: String = ""
    var event: String = ""
}

internal class RealmRoomAccountDataRepository(
    private val realm: Realm,
    private val json: Json,
) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomAccountDataRepositoryKey): Map<String, Event.RoomAccountDataEvent<*>> {
        return realm.findByKey(key).associate {
            it.key to json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun getBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ): Event.RoomAccountDataEvent<*>? {
        return realm.findByKeys(firstKey, secondKey).find()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: RoomAccountDataRepositoryKey, value: Map<String, Event.RoomAccountDataEvent<*>>) {
        value.entries.forEach { (secondKey, event) ->
            realm.write {
                val existing = findByKeys(key, secondKey).find()
                val upsert = (existing ?: RealmRoomAccountData()).apply {
                    this.roomId = key.roomId.full
                    this.type = key.type
                    this.key = secondKey
                    this.event = json.encodeToString(serializer, event)
                }
                if (existing == null) {
                    copyToRealm(upsert)
                }
            }
        }
    }

    override suspend fun saveBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: Event.RoomAccountDataEvent<*>
    ) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey).find()
            val upsert = (existing ?: RealmRoomAccountData()).apply {
                this.roomId = firstKey.roomId.full
                this.type = firstKey.type
                this.key = secondKey
                this.event = json.encodeToString(serializer, value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: RoomAccountDataRepositoryKey) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmRoomAccountData>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: RoomAccountDataRepositoryKey) =
        query<RealmRoomAccountData>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun MutableRealm.findByKey(key: RoomAccountDataRepositoryKey) =
        query<RealmRoomAccountData>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun Realm.findByKeys(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ) = query<RealmRoomAccountData>(
        "roomId == $0 && type == $1 && key == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()

    private fun MutableRealm.findByKeys(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ) = query<RealmRoomAccountData>(
        "roomId == $0 && type == $1 && key == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()
}