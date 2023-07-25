package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

data class RedactedMessageEventContent(
    val eventType: String,
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null,
    override val mentions: Mentions? = null,
) : MessageEventContent