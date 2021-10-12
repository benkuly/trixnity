package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId

data class RoomTimelineKey(
    val eventId: MatrixId.EventId,
    val roomId: MatrixId.RoomId
)
