package de.connect2x.trixnity.clientserverapi.model.room

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent

@Serializable
data class GetRelationsResponse(
    @SerialName("prev_batch") val start: String? = null,
    @SerialName("next_batch") val end: String? = null,
    @SerialName("chunk") val chunk: List<@Contextual RoomEvent<*>>,
    @SerialName("recursion_depth") val recursionDepth: Long? = null,
)