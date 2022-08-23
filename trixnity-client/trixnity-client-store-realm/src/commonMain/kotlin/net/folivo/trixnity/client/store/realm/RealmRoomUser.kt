package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

internal class RealmRoomUser : RealmObject {
    var userId: String = ""
    var roomId: String = ""
    var value: String = ""
}

internal class RealmRoomUserRepository(
    private val realm: Realm,
    private val json: Json,
) : RoomUserRepository {
    override suspend fun get(key: RoomId): Map<UserId, RoomUser> {
        return realm.findByKey(key).associate {
            UserId(it.userId) to json.decodeFromString(it.value)
        }
    }

    override suspend fun getBySecondKey(firstKey: RoomId, secondKey: UserId): RoomUser? {
        return realm.findByKeys(firstKey, secondKey).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) {
        value.entries.forEach { (secondKey, roomUser) ->
            realm.write {
                val existing = findByKeys(key, secondKey).find()
                val upsert = (existing ?: RealmRoomUser()).apply {
                    roomId = key.full
                    userId = secondKey.full
                    this.value = json.encodeToString(roomUser)
                }
                if (existing == null) {
                    copyToRealm(upsert)
                }
            }
        }
    }

    override suspend fun saveBySecondKey(firstKey: RoomId, secondKey: UserId, value: RoomUser) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey).find()
            val upsert = (existing ?: RealmRoomUser()).apply {
                roomId = firstKey.full
                userId = secondKey.full
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

    override suspend fun deleteBySecondKey(firstKey: RoomId, secondKey: UserId) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmRoomUser>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: RoomId) = query<RealmRoomUser>("roomId == $0", key.full).find()
    private fun MutableRealm.findByKey(key: RoomId) = query<RealmRoomUser>("roomId == $0", key.full).find()

    private fun Realm.findByKeys(
        firstKey: RoomId,
        secondKey: UserId
    ) = query<RealmRoomUser>("roomId == $0 && userId == $1", firstKey.full, secondKey.full).first()

    private fun MutableRealm.findByKeys(
        firstKey: RoomId,
        secondKey: UserId
    ) = query<RealmRoomUser>("roomId == $0 && userId == $1", firstKey.full, secondKey.full).first()

}