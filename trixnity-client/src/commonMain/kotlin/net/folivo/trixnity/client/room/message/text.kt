package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.EmoteMessageEventContent
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
                    ?.let { roomService.getTimelineEvent(roomId, it).firstWithContent() }
                val repliedEventContent = repliedEvent?.content?.getOrNull()
                val (richReplyBody, richReplyFormattedBody) =
                    computeRichReplies(repliedEvent, body, repliedEventContent, formattedBody)
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

internal fun computeRichReplies(
    repliedEvent: TimelineEvent?,
    body: String,
    repliedEventContent: RoomEventContent?,
    formattedBody: String?
): Pair<String, String?> {
    val richReplyBody = if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
        val sender = "<${repliedEvent.event.sender.full}>"
        when (repliedEventContent) {
            is EmoteMessageEventContent -> "* $sender ${repliedEventContent.body}".fallback() + "\n$body"
            else -> "$sender ${repliedEventContent.body}".fallback() + "\n$body"
        }
    } else {
        body
    }
    val richReplyFormattedBody =
        if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
            """
            <mx-reply>
            <blockquote>
            <a href="https://matrix.to/#/${repliedEvent.roomId.full}/${repliedEvent.eventId.full}">In reply to</a>
            ${if (repliedEventContent is EmoteMessageEventContent) "* " else ""}<a href="https://matrix.to/#/${repliedEvent.event.sender.full}">${repliedEvent.event.sender.full}</a>
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
    return Pair(richReplyBody, richReplyFormattedBody)
}

private fun String.fallback(): String = this.splitToSequence("\n").joinToString("\n") { "> $it" }