package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import kotlin.coroutines.CoroutineContext

class SqlDelightDeviceKeysRepository(
    private val db: DeviceKeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : DeviceKeysRepository {
    override suspend fun get(key: MatrixId.UserId): Map<String, DeviceKeys>? = withContext(context) {
        db.getDeviceKeys(key.full).executeAsOneOrNull()?.let {
            json.decodeFromString<Map<String, DeviceKeys>>(it)
        }
    }

    override suspend fun save(key: MatrixId.UserId, value: Map<String, DeviceKeys>) = withContext(context) {
        db.saveDeviceKeys(key.full, json.encodeToString(value))
    }

    override suspend fun delete(key: MatrixId.UserId) = withContext(context) {
        db.deleteDeviceKeys(key.full)
    }
}