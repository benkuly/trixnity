package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

interface TimelineEventRepository : DeleteByRoomIdMinimalRepository<TimelineEventKey, TimelineEvent> {
    override fun serializeKey(key: TimelineEventKey): String =
        key.roomId.full + key.eventId.full
}

data class TimelineEventKey(
    val eventId: EventId,
    val roomId: RoomId
)