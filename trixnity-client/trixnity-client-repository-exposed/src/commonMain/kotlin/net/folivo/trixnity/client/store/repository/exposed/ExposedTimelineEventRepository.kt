package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.core.model.RoomId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedTimelineEvent : Table("room_timeline_event") {
    val roomId = varchar("room_id", length = 255)
    val eventId = varchar("event_id", length = 255)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val value = text("value")
}

internal class ExposedTimelineEventRepository(private val json: Json) : TimelineEventRepository {
    override suspend fun get(key: TimelineEventKey): TimelineEvent? = withExposedRead {
        ExposedTimelineEvent.select {
            ExposedTimelineEvent.eventId.eq(key.eventId.full) and ExposedTimelineEvent.roomId.eq(key.roomId.full)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedTimelineEvent.value])
        }
    }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withExposedWrite {
        ExposedTimelineEvent.deleteWhere { ExposedTimelineEvent.roomId.eq(roomId.full) }
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent): Unit = withExposedWrite {
        ExposedTimelineEvent.replace {
            it[eventId] = key.eventId.full
            it[roomId] = key.roomId.full
            it[ExposedTimelineEvent.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: TimelineEventKey): Unit = withExposedWrite {
        ExposedTimelineEvent.deleteWhere {
            eventId.eq(key.eventId.full) and roomId.eq(key.roomId.full)
        }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedTimelineEvent.deleteAll()
    }
}