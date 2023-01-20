package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
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
        query<RealmRoomKeyRequest>().find().map {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun get(key: String): StoredRoomKeyRequest? = withRealmRead {
        findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: String, value: StoredRoomKeyRequest) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmRoomKeyRequest().apply { id = key }).apply {
            this.value = json.encodeToString(value)
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
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
