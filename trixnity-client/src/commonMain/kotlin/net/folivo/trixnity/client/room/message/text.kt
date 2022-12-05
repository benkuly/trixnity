package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent

@TrixnityDsl
fun MessageBuilder.text(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    contentBuilder = { relatesTo ->
        when (relatesTo) {
            is RelatesTo.Replace -> TextMessageEventContent(
                body = "*$body",
                format = format,
                formattedBody = formattedBody?.let { "*$it" },
                relatesTo = relatesTo.copy(newContent = TextMessageEventContent(body, format, formattedBody))
            )

            else -> TextMessageEventContent(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo
            )
        }
    }
}