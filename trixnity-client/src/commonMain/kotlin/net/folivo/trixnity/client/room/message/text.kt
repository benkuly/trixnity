package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent

@TrixnityDsl
fun MessageBuilder.text(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    content = TextMessageEventContent(body, format, formattedBody)
}