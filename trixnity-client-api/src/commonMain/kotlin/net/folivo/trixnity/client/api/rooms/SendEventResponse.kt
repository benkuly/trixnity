package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.EventId

@Serializable
data class SendEventResponse(
    @SerialName("event_id") val eventId: EventId
)