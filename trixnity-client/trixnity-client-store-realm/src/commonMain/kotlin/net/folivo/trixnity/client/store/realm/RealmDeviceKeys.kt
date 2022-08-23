package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
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
    private val realm: Realm,
    private val json: Json
) : DeviceKeysRepository {
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? {
        return realm.findByKey(key).find()?.let { realmDeviceKeys ->
            json.decodeFromString<Map<String, StoredDeviceKeys>>(realmDeviceKeys.value)
        }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmDeviceKeys().apply { userId = key.full }).apply {
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
            val existing = query<RealmDeviceKeys>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: UserId) = query<RealmDeviceKeys>("userId == $0", key.full).first()
    private fun MutableRealm.findByKey(key: UserId) = query<RealmDeviceKeys>("userId == $0", key.full).first()

}