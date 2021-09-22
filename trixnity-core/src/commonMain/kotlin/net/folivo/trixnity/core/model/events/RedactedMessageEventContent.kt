package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable

@Serializable
data class RedactedMessageEventContent(val eventType: String) : MessageEventContent