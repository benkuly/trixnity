package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.core.model.UserId

internal class RealmCrossSigningKeys : RealmObject {
    @PrimaryKey
    var userId: String = ""
    var value: String = ""
}

internal class RealmCrossSigningKeysRepository(
    private val realm: Realm,
    private val json: Json
) : CrossSigningKeysRepository {
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? {
        return realm.findByKey(key).find()?.let { crossSigningKeys ->
            json.decodeFromString<Set<StoredCrossSigningKeys>>(crossSigningKeys.value)
        }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmCrossSigningKeys().apply { userId = key.full }).apply {
                this.value = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: UserId) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmCrossSigningKeys>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: UserId) = query<RealmCrossSigningKeys>("userId == $0", key.full).first()
    private fun MutableRealm.findByKey(key: UserId) = query<RealmCrossSigningKeys>("userId == $0", key.full).first()
}
