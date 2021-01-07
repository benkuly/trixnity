package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StateEvent

@Serializable
data class GetEventsResponse(
    @SerialName("start") val start: String,
    @SerialName("end") val end: String,
    @SerialName("chunk") val chunk: List<RoomEvent<@Polymorphic Any>>,
    @SerialName("state") val state: List<StateEvent<@Polymorphic Any>>
)