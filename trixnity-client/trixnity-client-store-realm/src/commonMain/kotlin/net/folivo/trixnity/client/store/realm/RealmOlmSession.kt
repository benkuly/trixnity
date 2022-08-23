package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredOlmSession

internal class RealmOlmSession : RealmObject {
    @PrimaryKey
    var senderKey: String = ""
    var value: String = ""
}

internal class RealmOlmSessionRepository(
    private val realm: Realm,
    private val json: Json,
) : OlmSessionRepository {
    override suspend fun get(key: Key.Curve25519Key): Set<StoredOlmSession>? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Key.Curve25519Key, value: Set<StoredOlmSession>) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmOlmSession().apply { senderKey = key.value }).apply {
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: Key.Curve25519Key) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmOlmSession>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: Key.Curve25519Key) =
        query<RealmOlmSession>("senderKey == $0", key.value).first()

    private fun MutableRealm.findByKey(key: Key.Curve25519Key) =
        query<RealmOlmSession>("senderKey == $0", key.value).first()
}
