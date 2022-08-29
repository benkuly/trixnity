package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

interface TimelineEventRepository : MinimalStoreRepository<TimelineEventKey, TimelineEvent>

data class TimelineEventKey(
    val eventId: EventId,
    val roomId: RoomId
)