package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
import net.folivo.trixnity.client.store.repository.RoomKeyRequestRepository

internal class RealmRoomKeyRequest : RealmObject {
    @PrimaryKey
    var id: String = ""
    var value: String = ""
}

internal class RealmRoomKeyRequestRepository(
    private val json: Json,
) : RoomKeyRequestRepository {
    override suspend fun getAll(): List<StoredRoomKeyRequest> = withRealmRead {
        query<RealmRoomKeyRequest>().find().copyFromRealm().map {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun get(key: String): StoredRoomKeyRequest? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: String, value: StoredRoomKeyRequest): Unit = withRealmWrite {
        copyToRealm(
            RealmRoomKeyRequest().apply {
                id = key
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: String) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoomKeyRequest>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: String) = query<RealmRoomKeyRequest>("id == $0", key).first()
}
