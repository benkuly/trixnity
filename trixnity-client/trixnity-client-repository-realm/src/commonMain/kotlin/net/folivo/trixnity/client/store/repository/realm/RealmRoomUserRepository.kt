package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
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
    private val json: Json,
) : RoomUserRepository {
    override suspend fun get(key: RoomId): Map<UserId, RoomUser> = withRealmRead {
        findByKey(key).associate {
            UserId(it.userId) to json.decodeFromString(it.value)
        }
    }

    override suspend fun getBySecondKey(firstKey: RoomId, secondKey: UserId): RoomUser? = withRealmRead {
        findByKeys(firstKey, secondKey).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) = withRealmWrite {
        value.entries.forEach { (secondKey, roomUser) ->
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

    override suspend fun saveBySecondKey(firstKey: RoomId, secondKey: UserId, value: RoomUser) = withRealmWrite {
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

    override suspend fun delete(key: RoomId) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteBySecondKey(firstKey: RoomId, secondKey: UserId) = withRealmWrite {
        val existing = findByKeys(firstKey, secondKey)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoomUser>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: RoomId) = query<RealmRoomUser>("roomId == $0", key.full).find()

    private fun TypedRealm.findByKeys(
        firstKey: RoomId,
        secondKey: UserId
    ) = query<RealmRoomUser>("roomId == $0 && userId == $1", firstKey.full, secondKey.full).first()
}