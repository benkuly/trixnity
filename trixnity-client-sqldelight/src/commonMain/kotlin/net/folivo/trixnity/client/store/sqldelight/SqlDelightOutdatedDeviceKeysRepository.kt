package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutdatedDeviceKeysRepository
import net.folivo.trixnity.core.model.MatrixId
import kotlin.coroutines.CoroutineContext

class SqlDelightOutdatedDeviceKeysRepository(
    private val db: DeviceKeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : OutdatedDeviceKeysRepository {
    override suspend fun get(key: Long): Set<MatrixId.UserId>? = withContext(context) {
        db.getOutdatedDeviceKeys(key).executeAsOneOrNull()
            ?.let {
                it.outdated_device_keys
                    ?.let { outdated -> json.decodeFromString<Set<MatrixId.UserId>>(outdated) }
            }
    }

    override suspend fun save(key: Long, value: Set<MatrixId.UserId>) = withContext(context) {
        db.saveOutdatedDeviceKeys(Sql_outdated_device_keys(key, json.encodeToString(value)))
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteOutdatedDeviceKeys(key)
    }
}