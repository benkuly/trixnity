package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.client.store.sqldelight.KeysQueries
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightDeviceKeysRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : DeviceKeysRepository {
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? = withContext(context) {
        db.getDeviceKeys(key.full).executeAsOneOrNull()?.let {
            json.decodeFromString<Map<String, StoredDeviceKeys>>(it)
        }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) = withContext(context) {
        db.saveDeviceKeys(key.full, json.encodeToString(value))
    }

    override suspend fun delete(key: UserId) = withContext(context) {
        db.deleteDeviceKeys(key.full)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllDeviceKeys()
    }
}