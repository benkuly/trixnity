package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.roomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.bodyWithoutFallback
import net.folivo.trixnity.core.model.events.m.room.formattedBodyWithoutFallback

internal fun computeRichReplies(
    repliedEvent: TimelineEvent?,
    body: String,
    formattedBody: String?
): Pair<String, String?> {
    val repliedEventContent = repliedEvent?.content?.getOrNull()
    val richReplyBody =
        if (repliedEvent != null && repliedEventContent is RoomMessageEventContent) {
            val sender = "<${repliedEvent.event.sender.full}>"
            when (repliedEventContent) {
                is RoomMessageEventContent.TextBased.Emote -> "* $sender ${repliedEventContent.bodyWithoutFallback}".asFallback()
                else -> "$sender ${repliedEventContent.bodyWithoutFallback}".asFallback()
            } + """
                
                
                $body
            """.trimIndent()
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
                    ${if (repliedEventContent is RoomMessageEventContent.TextBased.Emote) "* " else ""}<a href="https://matrix.to/#/${repliedEvent.event.sender.full}">${repliedEvent.event.sender.full}</a>
                    <br />
                    """.trimIndent()
                )
                appendLine(
                    repliedEventContent.formattedBodyWithoutFallback
                        ?: repliedEventContent.bodyWithoutFallback.replace("\n", "<br />")
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

private fun String.asFallback(): String = this.lineSequence().joinToString("\n") { "> $it" }