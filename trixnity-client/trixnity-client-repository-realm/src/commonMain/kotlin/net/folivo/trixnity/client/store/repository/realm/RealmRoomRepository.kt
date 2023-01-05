package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
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
    private val json: Json,
) : RoomRepository {
    override suspend fun getAll(): List<Room> = withRealmRead {
        query<RealmRoom>().find().map {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun get(key: RoomId): Room? = withRealmRead {
        findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: RoomId, value: Room) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmRoom().apply { roomId = key.full }).apply {
            this.value = json.encodeToString(value)
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun delete(key: RoomId) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoom>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: RoomId) = query<RealmRoom>("roomId == $0", key.full).first()
}