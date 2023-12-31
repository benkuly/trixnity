package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
fun MessageBuilder.emote(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    contentBuilder = { relatesTo, mentions, newContentMentions ->
        when (relatesTo) {
            is RelatesTo.Replace -> RoomMessageEventContent.TextBased.Emote(
                body = "* $body",
                format = format,
                formattedBody = formattedBody?.let { "* $it" },
                relatesTo = relatesTo.copy(
                    newContent = RoomMessageEventContent.TextBased.Emote(
                        body = body,
                        format = format,
                        formattedBody = formattedBody,
                        mentions = newContentMentions,
                    )
                ),
                mentions = mentions,
            )

            is RelatesTo.Reply, is RelatesTo.Thread -> {
                val repliedEvent = relatesTo.replyTo?.eventId
                    ?.let { roomService.getTimelineEventWithContentAndTimeout(roomId, it) }
                val (richReplyBody, richReplyFormattedBody) =
                    computeRichReplies(repliedEvent, body, formattedBody)
                RoomMessageEventContent.TextBased.Emote(
                    body = richReplyBody,
                    format = "org.matrix.custom.html",
                    formattedBody = richReplyFormattedBody,
                    relatesTo = relatesTo,
                    mentions = mentions,
                )
            }

            else -> RoomMessageEventContent.TextBased.Emote(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo,
                mentions = mentions,
            )
        }
    }
}
