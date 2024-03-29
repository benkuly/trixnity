package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
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
    private val json: Json
) : CrossSigningKeysRepository {
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let { crossSigningKeys ->
            json.decodeFromString<Set<StoredCrossSigningKeys>>(crossSigningKeys.value)
        }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>): Unit = withRealmWrite {
        copyToRealm(
            RealmCrossSigningKeys().apply {
                userId = key.full
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: UserId) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmCrossSigningKeys>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: UserId) = query<RealmCrossSigningKeys>("userId == $0", key.full).first()
}
