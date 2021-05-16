package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable

@Serializable
data class RedactedBasicEventContent(val eventType: String) : EventContent