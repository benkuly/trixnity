package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredNotificationState
import net.folivo.trixnity.client.store.repository.NotificationStateRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedNotificationState : Table("notification_state") {
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedNotificationStateRepository(
    private val json: Json,
) : NotificationStateRepository {

    override suspend fun getAll(): List<StoredNotificationState> = withExposedRead {
        ExposedNotificationState.selectAll().map { json.decodeFromString(it[ExposedNotificationState.value]) }
    }

    override suspend fun get(key: RoomId): StoredNotificationState? = withExposedRead {
        ExposedNotificationState.selectAll().where {
            ExposedNotificationState.roomId.eq(key.full)
        }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotificationState.value]) }
    }

    override suspend fun save(key: RoomId, value: StoredNotificationState) = withExposedWrite {
        ExposedNotificationState.upsert {
            it[roomId] = key.full
            it[ExposedNotificationState.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomId) = withExposedWrite {
        ExposedNotificationState.deleteWhere { roomId.eq(key.full) }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedNotificationState.deleteAll()
    }
}