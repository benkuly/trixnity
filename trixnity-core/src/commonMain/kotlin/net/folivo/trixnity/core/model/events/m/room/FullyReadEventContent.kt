package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

@Serializable
data class FullyReadEventContent(
    @SerialName("event_id") val eventId: EventId,
) : RoomAccountDataEventContent