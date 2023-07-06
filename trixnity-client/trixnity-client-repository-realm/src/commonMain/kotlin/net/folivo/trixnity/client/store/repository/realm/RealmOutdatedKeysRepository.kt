package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.core.model.UserId

internal class RealmOutdatedKeys : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var value: String = ""
}

internal class RealmOutdatedKeysRepository(
    private val json: Json,
) : OutdatedKeysRepository {
    override suspend fun get(key: Long): Set<UserId>? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Long, value: Set<UserId>): Unit = withRealmWrite {
        copyToRealm(
            RealmOutdatedKeys().apply {
                id = key
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: Long) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmOutdatedKeys>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Long) = query<RealmOutdatedKeys>("id == $0", key).first()
}