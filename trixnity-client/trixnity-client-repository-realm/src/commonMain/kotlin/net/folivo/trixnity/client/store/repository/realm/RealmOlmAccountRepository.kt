package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
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
        findByKey(key).find()?.copyFromRealm()?.pickled
    }

    override suspend fun save(key: Long, value: String): Unit = withRealmWrite {
        copyToRealm(
            RealmOlmAccount().apply {
                id = key
                pickled = value
            },
            UpdatePolicy.ALL
        )
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