package de.connect2x.trixnity.clientserverapi.model.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId

@Serializable
data class SendEventResponse(
    @SerialName("event_id") val eventId: EventId
)