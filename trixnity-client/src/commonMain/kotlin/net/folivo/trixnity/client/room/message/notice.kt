package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.NoticeMessageEventContent

@TrixnityDsl
fun MessageBuilder.notice(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    content = NoticeMessageEventContent(body, format, formattedBody)
}