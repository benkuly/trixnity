package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.repository.OlmAccountRepository

internal class RealmOlmAccount : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var pickled: String = ""
}

internal class RealmOlmAccountRepository(
    private val realm: Realm,
) : OlmAccountRepository {
    override suspend fun get(key: Long): String? {
        return realm.findByKey(key).find()?.pickled
    }

    override suspend fun save(key: Long, value: String) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmOlmAccount().apply {
                id = key
            }).apply {
                pickled = value
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
            val existing = query<RealmOlmAccount>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: Long) = query<RealmOlmAccount>("id == $0", key).first()
    private fun MutableRealm.findByKey(key: Long) = query<RealmOlmAccount>("id == $0", key).first()
}