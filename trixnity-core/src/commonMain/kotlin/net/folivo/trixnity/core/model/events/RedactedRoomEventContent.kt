package net.folivo.trixnity.core.model.events

import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

data class RedactedEventContent(val eventType: String) :
    RoomEventContent, MessageEventContent, StateEventContent {
    // TODO serialize when MSC3389 is in spec
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
    override val externalUrl: String? = null
}