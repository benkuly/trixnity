package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository

internal class RealmSecretKeyRequest : RealmObject {
    @PrimaryKey
    var id: String = ""
    var value: String = ""
}

internal class RealmSecretKeyRequestRepository(
    private val realm: Realm,
    private val json: Json,
) : SecretKeyRequestRepository {
    override suspend fun getAll(): List<StoredSecretKeyRequest> {
        return realm.query<RealmSecretKeyRequest>().find().map {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun get(key: String): StoredSecretKeyRequest? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: String, value: StoredSecretKeyRequest) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmSecretKeyRequest().apply { id = key }).apply {
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: String) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmSecretKeyRequest>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: String) = query<RealmSecretKeyRequest>("id == $0", key).first()
    private fun MutableRealm.findByKey(key: String) = query<RealmSecretKeyRequest>("id == $0", key).first()
}
