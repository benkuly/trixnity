package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.EmoteMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.getFormattedBody

internal fun computeRichReplies(
    repliedEvent: TimelineEvent?,
    body: String,
    formattedBody: String?
): Pair<String, String?> {
    val repliedEventContent = repliedEvent?.content?.getOrNull()
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
            buildString {
                appendLine(
                    """
                    <mx-reply>
                    <blockquote>
                    <a href="https://matrix.to/#/${repliedEvent.roomId.full}/${repliedEvent.eventId.full}">In reply to</a>
                    ${if (repliedEventContent is EmoteMessageEventContent) "* " else ""}<a href="https://matrix.to/#/${repliedEvent.event.sender.full}">${repliedEvent.event.sender.full}</a>
                    <br />
                    """.trimIndent()
                )
                appendLine(
                    repliedEventContent.getFormattedBody()
                        ?: repliedEventContent.body.replace("\n", "<br />")
                )
                appendLine(
                    """
                    </blockquote>
                    </mx-reply>
                    """.trimIndent()
                )
                append(formattedBody ?: body.replace("\n", "<br />"))
            }
        } else formattedBody
    return Pair(richReplyBody, richReplyFormattedBody)
}

private fun String.asFallback(): String = this.splitToSequence("\n").joinToString("\n") { "> $it" }