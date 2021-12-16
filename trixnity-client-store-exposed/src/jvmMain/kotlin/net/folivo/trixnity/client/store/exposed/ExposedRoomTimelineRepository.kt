package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.RoomTimelineRepository
import org.jetbrains.exposed.sql.*

internal object ExposedRoomTimeline : Table("room_timeline") {
    val roomId = varchar("room_id", length = 65535)
    val eventId = varchar("event_id", length = 65535)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val room = text("room")
}

internal class ExposedRoomTimelineRepository(private val json: Json) : RoomTimelineRepository {
    override suspend fun get(key: RoomTimelineKey): TimelineEvent? {
        return ExposedRoomTimeline.select {
            ExposedRoomTimeline.eventId.eq(key.eventId.full) and ExposedRoomTimeline.roomId.eq(key.roomId.full)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoomTimeline.room])
        }
    }

    override suspend fun save(key: RoomTimelineKey, value: TimelineEvent) {
        ExposedRoomTimeline.replace {
            it[eventId] = key.eventId.full
            it[roomId] = key.roomId.full
            it[room] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomTimelineKey) {
        ExposedRoomTimeline.deleteWhere {
            ExposedRoomTimeline.eventId.eq(key.eventId.full) and ExposedRoomTimeline.roomId.eq(key.roomId.full)
        }
    }
}