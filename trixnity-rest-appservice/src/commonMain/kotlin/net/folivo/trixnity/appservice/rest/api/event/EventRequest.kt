package net.folivo.trixnity.appservice.rest.api.event

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.Event

@Serializable
data class EventRequest(
    @SerialName("events")
    val events: List<@Contextual Event<*>>
)