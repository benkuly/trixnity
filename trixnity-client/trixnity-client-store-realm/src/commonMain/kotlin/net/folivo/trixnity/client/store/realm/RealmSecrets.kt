package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.crypto.SecretType

internal class RealmSecrets : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var value: String = ""
}

internal class RealmSecretsRepository(
    private val realm: Realm,
    private val json: Json,
) : SecretsRepository {
    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmSecrets().apply {
                id = key
            }).apply {
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: Long) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmSecrets>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: Long) = query<RealmSecrets>("id == $0", key).first()
    private fun MutableRealm.findByKey(key: Long) = query<RealmSecrets>("id == $0", key).first()
}
