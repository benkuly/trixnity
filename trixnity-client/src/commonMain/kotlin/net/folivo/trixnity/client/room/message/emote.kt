package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.EmoteMessageEventContent

@TrixnityDsl
fun MessageBuilder.emote(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    contentBuilder = { relatesTo ->
        when (relatesTo) {
            is RelatesTo.Replace -> EmoteMessageEventContent(
                body = "*$body",
                format = format,
                formattedBody = formattedBody?.let { "*$it" },
                relatesTo = relatesTo.copy(
                    newContent = EmoteMessageEventContent(
                        body,
                        format,
                        formattedBody
                    )
                )
            )

            else -> EmoteMessageEventContent(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo
            )
        }
    }
}