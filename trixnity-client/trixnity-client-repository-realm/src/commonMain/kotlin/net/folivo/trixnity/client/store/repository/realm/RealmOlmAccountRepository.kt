package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.repository.OlmAccountRepository

internal class RealmOlmAccount : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var pickled: String = ""
}

internal class RealmOlmAccountRepository : OlmAccountRepository {
    override suspend fun get(key: Long): String? = withRealmRead {
        findByKey(key).find()?.pickled
    }

    override suspend fun save(key: Long, value: String) = withRealmWrite {
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

    override suspend fun delete(key: Long) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmOlmAccount>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Long) = query<RealmOlmAccount>("id == $0", key).first()
}