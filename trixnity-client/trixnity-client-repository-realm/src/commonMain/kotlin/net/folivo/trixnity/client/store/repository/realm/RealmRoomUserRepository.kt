package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

internal class RealmRoomUser : RealmObject {
    @PrimaryKey
    var id: String = ""

    var userId: String = ""
    var roomId: String = ""
    var value: String = ""
}

internal class RealmRoomUserRepository(
    private val json: Json,
) : RoomUserRepository {
    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUser> = withRealmRead {
        findByKey(firstKey).copyFromRealm().associate {
            UserId(it.userId) to json.decodeFromString(it.value)
        }
    }

    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUser? = withRealmRead {
        findByKeys(firstKey, secondKey).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUser): Unit = withRealmWrite {
        copyToRealm(
            RealmRoomUser().apply {
                id = serializeKey(firstKey, secondKey)
                roomId = firstKey.full
                userId = secondKey.full
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = findByKey(roomId)
        delete(existing)
    }

    override suspend fun delete(firstKey: RoomId, secondKey: UserId) = withRealmWrite {
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