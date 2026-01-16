package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
import de.connect2x.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedNotificationUpdate : Table("notification_update") {
    val id = varchar("id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedNotificationUpdateRepository(
    private val json: Json,
) : NotificationUpdateRepository {

    override suspend fun getAll(): List<StoredNotificationUpdate> = withExposedRead {
        ExposedNotificationUpdate.selectAll().map { json.decodeFromString(it[ExposedNotificationUpdate.value]) }
    }

    override suspend fun get(key: String): StoredNotificationUpdate? = withExposedRead {
        ExposedNotificationUpdate.selectAll().where {
            ExposedNotificationUpdate.id.eq(key)
        }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotificationUpdate.value]) }
    }

    override suspend fun save(key: String, value: StoredNotificationUpdate) = withExposedWrite {
        ExposedNotificationUpdate.upsert {
            it[roomId] = value.roomId.full
            it[id] = key
            it[ExposedNotificationUpdate.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: String) = withExposedWrite {
        ExposedNotificationUpdate.deleteWhere { id.eq(key) }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedNotificationUpdate.deleteAll()
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedNotificationUpdate.deleteWhere { ExposedNotificationUpdate.roomId.eq(roomId.full) }
    }
}