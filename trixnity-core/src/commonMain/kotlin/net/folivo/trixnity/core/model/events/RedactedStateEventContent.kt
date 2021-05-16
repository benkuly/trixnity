package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable

@Serializable
data class RedactedStateEventContent(val eventType: String) : StateEventContent