package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.core.model.UserId

internal class RealmDeviceKeys : RealmObject {
    @PrimaryKey
    var userId: String = ""
    var value: String = ""
}

internal class RealmDeviceKeysRepository(
    private val json: Json
) : DeviceKeysRepository {
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let { realmDeviceKeys ->
            json.decodeFromString<Map<String, StoredDeviceKeys>>(realmDeviceKeys.value)
        }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>): Unit = withRealmWrite {
        copyToRealm(
            RealmDeviceKeys().apply {
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
        val existing = query<RealmDeviceKeys>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: UserId) = query<RealmDeviceKeys>("userId == $0", key.full).first()
}