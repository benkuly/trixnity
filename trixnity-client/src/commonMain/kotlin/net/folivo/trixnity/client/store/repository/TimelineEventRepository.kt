package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

interface TimelineEventRepository : MinimalDeleteByRoomIdRepository<TimelineEventKey, TimelineEvent> {
    override fun serializeKey(key: TimelineEventKey): String =
        key.roomId.full + key.eventId.full
}

data class TimelineEventKey(
    val eventId: EventId,
    val roomId: RoomId
)