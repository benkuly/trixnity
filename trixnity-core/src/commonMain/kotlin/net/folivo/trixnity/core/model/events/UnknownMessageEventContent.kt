package net.folivo.trixnity.core.model.events

import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

data class UnknownMessageEventContent(
    val raw: JsonObject,
    val eventType: String
) : MessageEventContent {
    // is always null, because this class is the last fallback, when nothing can be deserialized
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
}