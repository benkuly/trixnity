package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.client.store.sqldelight.KeysQueries
import net.folivo.trixnity.client.store.sqldelight.Sql_outdated_device_keys
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightOutdatedDeviceKeysRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : OutdatedKeysRepository {
    override suspend fun get(key: Long): Set<UserId>? = withContext(context) {
        db.getOutdatedDeviceKeys(key).executeAsOneOrNull()
            ?.let {
                it.outdated_device_keys
                    ?.let { outdated -> json.decodeFromString<Set<UserId>>(outdated) }
            }
    }

    override suspend fun save(key: Long, value: Set<UserId>) = withContext(context) {
        db.saveOutdatedDeviceKeys(Sql_outdated_device_keys(key, json.encodeToString(value)))
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteOutdatedDeviceKeys(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllOutdatedDeviceKeys()
    }
}