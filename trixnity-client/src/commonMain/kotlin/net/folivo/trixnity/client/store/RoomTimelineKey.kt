package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

data class RoomTimelineKey(
    val eventId: EventId,
    val roomId: RoomId
)
