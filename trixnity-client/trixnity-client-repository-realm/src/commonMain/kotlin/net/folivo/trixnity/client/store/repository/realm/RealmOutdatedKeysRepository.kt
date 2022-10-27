package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
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
        findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Long, value: Set<UserId>) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmOutdatedKeys().apply {
            id = key
        }).apply {
            this.value = json.encodeToString(value)
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
        val existing = query<RealmOutdatedKeys>().find()
        delete(existing)
    }

    private fun Realm.findByKey(key: Long) = query<RealmOutdatedKeys>("id == $0", key).first()
    private fun MutableRealm.findByKey(key: Long) = query<RealmOutdatedKeys>("id == $0", key).first()

}