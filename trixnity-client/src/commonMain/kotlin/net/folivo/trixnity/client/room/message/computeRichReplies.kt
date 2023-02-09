package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.EmoteMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.getFormattedBody

internal fun computeRichReplies(
    repliedEvent: TimelineEvent?,
    body: String,
    repliedEventContent: RoomEventContent?,
    formattedBody: String?
): Pair<String, String?> {
    val richReplyBody = if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
        val sender = "<${repliedEvent.event.sender.full}>"
        when (repliedEventContent) {
            is EmoteMessageEventContent -> "* $sender ${repliedEventContent.body}".asFallback()
            else -> "$sender ${repliedEventContent.body}".asFallback()
        } + "\n\n$body"
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

private fun String.asFallback(): String = this.splitToSequence("\n").joinToString("\n") { "> $it" }