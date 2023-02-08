package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.room.firstWithContent
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
                body = "* $body",
                format = format,
                formattedBody = formattedBody?.let { "* $it" },
                relatesTo = relatesTo.copy(
                    newContent = EmoteMessageEventContent(
                        body,
                        format,
                        formattedBody
                    )
                )
            )

            is RelatesTo.Reply, is RelatesTo.Thread -> {
                val repliedEvent = relatesTo.replyTo?.eventId
                    ?.let { roomService.getTimelineEvent(it, roomId).firstWithContent() }
                val repliedEventContent = repliedEvent?.content?.getOrNull()
                val (richReplyBody, richReplyFormattedBody) =
                    computeRichReplies(repliedEvent, body, repliedEventContent, formattedBody)
                EmoteMessageEventContent(
                    body = richReplyBody,
                    format = format,
                    formattedBody = richReplyFormattedBody,
                    relatesTo = relatesTo
                )
            }

            else -> EmoteMessageEventContent(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo
            )
        }
    }
}
