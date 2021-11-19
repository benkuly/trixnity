package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

@Serializable
data class FullyReadEventContent(
    @SerialName("event_id") val eventId: EventId,
) : RoomAccountDataEventContent