package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.RoomTimelineEventRepository
import org.jetbrains.exposed.sql.*

internal object ExposedRoomTimelineEvent : Table("room_timeline_event") {
    val roomId = varchar("room_id", length = 255)
    val eventId = varchar("event_id", length = 255)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val value = text("value")
}

internal class ExposedRoomTimelineEventRepository(private val json: Json) : RoomTimelineEventRepository {
    override suspend fun get(key: RoomTimelineKey): TimelineEvent? {
        return ExposedRoomTimelineEvent.select {
            ExposedRoomTimelineEvent.eventId.eq(key.eventId.full) and ExposedRoomTimelineEvent.roomId.eq(key.roomId.full)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoomTimelineEvent.value])
        }
    }

    override suspend fun save(key: RoomTimelineKey, value: TimelineEvent) {
        ExposedRoomTimelineEvent.replace {
            it[eventId] = key.eventId.full
            it[roomId] = key.roomId.full
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: RoomTimelineKey) {
        ExposedRoomTimelineEvent.deleteWhere {
            ExposedRoomTimelineEvent.eventId.eq(key.eventId.full) and ExposedRoomTimelineEvent.roomId.eq(key.roomId.full)
        }
    }

    override suspend fun deleteAll() {
        ExposedRoomTimelineEvent.deleteAll()
    }
}