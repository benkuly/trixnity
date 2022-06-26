package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event

@Serializable
data class GetRelationsResponse(
    @SerialName("prev_batch") val start: String? = null,
    @SerialName("next_batch") val end: String? = null,
    @SerialName("chunk") val chunk: List<@Contextual Event.RoomEvent<*>>? = null,
)