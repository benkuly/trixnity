package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.room.firstWithContent
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.NoticeMessageEventContent

@TrixnityDsl
fun MessageBuilder.notice(
    body: String,
    format: String? = null,
    formattedBody: String? = null
) {
    contentBuilder = { relatesTo ->
        when (relatesTo) {
            is RelatesTo.Replace -> NoticeMessageEventContent(
                body = "* $body",
                format = format,
                formattedBody = formattedBody?.let { "* $it" },
                relatesTo = relatesTo.copy(
                    newContent = NoticeMessageEventContent(
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
                NoticeMessageEventContent(
                    body = richReplyBody,
                    format = format,
                    formattedBody = richReplyFormattedBody,
                    relatesTo = relatesTo
                )
            }

            else -> NoticeMessageEventContent(
                body = body,
                format = format,
                formattedBody = formattedBody,
                relatesTo = relatesTo
            )
        }
    }
}