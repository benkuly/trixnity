package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.RoomRepository
import net.folivo.trixnity.core.model.RoomId

internal class RealmRoom : RealmObject {
    @PrimaryKey
    var roomId: String = ""
    var value: String = ""
}

internal class RealmRoomRepository(
    private val realm: Realm,
    private val json: Json,
) : RoomRepository {
    override suspend fun getAll(): List<Room> {
        return realm.query<RealmRoom>().find().map {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun get(key: RoomId): Room? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: RoomId, value: Room) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmRoom().apply { roomId = value.roomId.full }).apply {
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: RoomId) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = realm.query<RealmRoom>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: RoomId) = query<RealmRoom>("roomId == $0", key.full).first()
    private fun MutableRealm.findByKey(key: RoomId) = query<RealmRoom>("roomId == $0", key.full).first()

}