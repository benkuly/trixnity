package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredNotification
import net.folivo.trixnity.client.store.repository.NotificationRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedNotification : Table("notification") {
    val id = varchar("id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedNotificationRepository(
    private val json: Json,
) : NotificationRepository {

    override suspend fun getAll(): List<StoredNotification> = withExposedRead {
        ExposedNotification.selectAll().map { json.decodeFromString(it[ExposedNotification.value]) }
    }

    override suspend fun get(key: String): StoredNotification? = withExposedRead {
        ExposedNotification.selectAll().where {
            ExposedNotification.id.eq(key)
        }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotification.value]) }
    }

    override suspend fun save(key: String, value: StoredNotification) = withExposedWrite {
        ExposedNotification.upsert {
            it[roomId] = value.roomId.full
            it[id] = key
            it[ExposedNotification.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: String) = withExposedWrite {
        ExposedNotification.deleteWhere { id.eq(key) }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedNotification.deleteAll()
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedNotification.deleteWhere { ExposedNotification.roomId.eq(roomId.full) }
    }
}