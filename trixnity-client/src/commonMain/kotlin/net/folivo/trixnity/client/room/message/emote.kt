package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.EmoteMessageEventContent

fun MessageBuilder.emote(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    content = EmoteMessageEventContent(body, format, formattedBody)
}