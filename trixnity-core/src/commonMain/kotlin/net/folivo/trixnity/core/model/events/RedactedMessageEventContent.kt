package net.folivo.trixnity.core.model.events

import kotlinx.serialization.Serializable

@Serializable
data class RedactedMessageEventContent(
    val eventType: String,
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null,
) : MessageEventContent