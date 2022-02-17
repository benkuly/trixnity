package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event.StateEvent

@Serializable
data class GetMembersResponse(
    @SerialName("chunk") val chunk: List<@Contextual StateEvent<*>>
)