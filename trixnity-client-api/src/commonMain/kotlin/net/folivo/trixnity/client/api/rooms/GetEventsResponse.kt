package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent

@Serializable
data class GetEventsResponse(
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
    @SerialName("chunk") val chunk: List<@Contextual RoomEvent<*>>?,
    @SerialName("state") val state: List<@Contextual StateEvent<*>>?
)