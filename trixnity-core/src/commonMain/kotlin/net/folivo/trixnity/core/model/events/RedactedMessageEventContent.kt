package net.folivo.trixnity.core.model.events

data class RedactedMessageEventContent(
    val eventType: String,
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null,
) : MessageEventContent