package net.folivo.trixnity.clientserverapi.model.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId

@Serializable
data class SendEventResponse(
    @SerialName("event_id") val eventId: EventId
)