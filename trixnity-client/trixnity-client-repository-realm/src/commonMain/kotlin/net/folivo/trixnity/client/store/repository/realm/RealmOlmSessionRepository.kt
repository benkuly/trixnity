package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
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
    private val json: Json,
) : OlmSessionRepository {
    override suspend fun get(key: Key.Curve25519Key): Set<StoredOlmSession>? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Key.Curve25519Key, value: Set<StoredOlmSession>): Unit = withRealmWrite {
        copyToRealm(
            RealmOlmSession().apply {
                senderKey = key.value
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: Key.Curve25519Key) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmOlmSession>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Key.Curve25519Key) =
        query<RealmOlmSession>("senderKey == $0", key.value).first()
}
