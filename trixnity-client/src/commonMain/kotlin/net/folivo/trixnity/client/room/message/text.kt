package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.getFormattedBody

@TrixnityDsl
fun MessageBuilder.text(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    contentBuilder = { relatesTo ->
        when (relatesTo) {
            is RelatesTo.Replace -> TextMessageEventContent(
                body = "* $body",
                format = format,
                formattedBody = formattedBody?.let { "* $it" },
                relatesTo = relatesTo.copy(newContent = TextMessageEventContent(body, format, formattedBody))
            )

            is RelatesTo.Reply, is RelatesTo.Thread -> {
                val repliedEvent = relatesTo.replyTo?.eventId
                    ?.let { roomService.getTimelineEvent(it, roomId).firstWithContent() }
                val repliedEventContent = repliedEvent?.content?.getOrNull()
                val richReplyBody = if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
                    "<${repliedEvent.event.sender.full}> ${repliedEventContent.body}"
                        .splitToSequence("\n").joinToString("\n") { "> $it" }
                } else body
                val richReplyFormattedBody =
                    if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
                        """
                        <mx-reply>
                        <blockquote>
                        <a href="https://matrix.to/#/${repliedEvent.roomId.full}/${repliedEvent.eventId.full}">In reply to</a>
                        <a href="https://matrix.to/#/${repliedEvent.event.sender.full}">${repliedEvent.event.sender.full}</a>
                        <br />
                        ${
                            repliedEventContent.getFormattedBody()
                                ?: repliedEventContent.body.replace("\n", "<br />")
                        }
                        </blockquote>
                        </mx-reply>
                        ${formattedBody ?: body.replace("\n", "<br />")}
                    """.trimIndent()
                    } else formattedBody
                TextMessageEventContent(
                    body = richReplyBody,
                    format = format,
                    formattedBody = richReplyFormattedBody,
                    relatesTo = relatesTo
                )
            }

            else -> TextMessageEventContent(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo
            )
        }
    }
}