package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.RoomTimelineEventRepository
import org.jetbrains.exposed.sql.*

internal object ExposedRoomTimeline : Table("room_timeline_event") {
    val roomId = varchar("room_id", length = 16383)
    val eventId = varchar("event_id", length = 16383)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val value = text("value")
}

internal class ExposedRoomTimelineEventRepository(private val json: Json) : RoomTimelineEventRepository {
    override suspend fun get(key: RoomTimelineKey): TimelineEvent? {
        return ExposedRoomTimeline.select {
            ExposedRoomTimeline.eventId.eq(key.eventId.full) and ExposedRoomTimeline.roomId.eq(key.roomId.full)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoomTimeline.value])
        }
    }

    override suspend fun save(key: RoomTimelineKey, value: TimelineEvent) {
        ExposedRoomTimeline.replace {
            it[eventId] = key.eventId.full
            it[roomId] = key.roomId.full
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomTimelineKey) {
        ExposedRoomTimeline.deleteWhere {
            ExposedRoomTimeline.eventId.eq(key.eventId.full) and ExposedRoomTimeline.roomId.eq(key.roomId.full)
        }
    }

    override suspend fun deleteAll() {
        ExposedRoomTimeline.deleteAll()
    }
}