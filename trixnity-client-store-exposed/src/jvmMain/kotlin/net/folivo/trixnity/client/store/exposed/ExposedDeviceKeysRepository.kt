package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedDeviceKeys : Table("device_keys") {
    val userId = varchar("user_id", length = 65535)
    override val primaryKey = PrimaryKey(userId)
    val deviceKeys = text("device_keys")
}

internal class ExposedDeviceKeysRepository(private val json: Json) : DeviceKeysRepository {
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? {
        return ExposedDeviceKeys.select { ExposedDeviceKeys.userId eq key.full }.firstOrNull()?.let {
            it[ExposedDeviceKeys.deviceKeys].let { deviceKeys ->
                json.decodeFromString<Map<String, StoredDeviceKeys>>(deviceKeys)
            }
        }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) {
        ExposedDeviceKeys.replace {
            it[userId] = key.full
            it[deviceKeys] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: UserId) {
        ExposedDeviceKeys.deleteWhere { ExposedDeviceKeys.userId eq key.full }
    }
}