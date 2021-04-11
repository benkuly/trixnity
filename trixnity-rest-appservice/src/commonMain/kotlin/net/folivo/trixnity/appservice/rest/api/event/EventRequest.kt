package net.folivo.trixnity.appservice.rest.api.event

import kotlinx.serialization.SerialName
import net.folivo.trixnity.core.model.events.Event

data class EventRequest(
    @SerialName("events")
    val events: List<Event<*>>
)