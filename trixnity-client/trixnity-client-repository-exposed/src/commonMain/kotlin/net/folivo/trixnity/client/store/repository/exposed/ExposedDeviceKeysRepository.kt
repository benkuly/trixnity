package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.repository.DeviceKeysRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedDeviceKeys : Table("device_keys") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedDeviceKeysRepository(private val json: Json) : DeviceKeysRepository {
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? = withExposedRead {
        ExposedDeviceKeys.select { ExposedDeviceKeys.userId eq key.full }.firstOrNull()?.let {
            it[ExposedDeviceKeys.value].let { deviceKeys ->
                json.decodeFromString<Map<String, StoredDeviceKeys>>(deviceKeys)
            }
        }
    }

    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>): Unit = withExposedWrite {
        ExposedDeviceKeys.upsert {
            it[userId] = key.full
            it[ExposedDeviceKeys.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: UserId): Unit = withExposedWrite {
        ExposedDeviceKeys.deleteWhere { userId eq key.full }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedDeviceKeys.deleteAll()
    }
}