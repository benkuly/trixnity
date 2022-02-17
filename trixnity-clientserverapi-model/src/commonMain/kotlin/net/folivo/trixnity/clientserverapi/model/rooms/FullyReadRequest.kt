package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId

@Serializable
data class FullyReadRequest(
    @SerialName("m.fully_read") val fullyRead: EventId,
    @SerialName("m.read") val read: EventId? = null,
)