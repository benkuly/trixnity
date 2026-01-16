package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.utils.TrixnityDsl

data class ContentBuilderInfo(
    val relatesTo: RelatesTo?,
    val mentions: Mentions?,
    val newContentMentions: Mentions?
)

typealias ContentBuilder = suspend ContentBuilderInfo.() -> MessageEventContent?

@TrixnityDsl
class MessageBuilder(
    val roomId: RoomId,
    val roomService: RoomService,
    val mediaService: MediaService,
    val ownUserId: UserId
) {
    var contentBuilder: ContentBuilder = { null }

    /**
     * This allows to set a [contentBuilder], that does not consider [RelatesTo] or [Mentions].
     */
    fun content(content: MessageEventContent) {
        contentBuilder = { content }
    }

    var relatesTo: RelatesTo? = null
    var mentions: Mentions? = Mentions() // empty to enable general m.mentions feature, even if there is no mention

    suspend fun build(builder: suspend MessageBuilder.() -> Unit): MessageEventContent? {
        builder()
        val relatesTo = relatesTo
        val mentions = mentions

        var addedMentions: Mentions? = mentions
        var newContentMentions: Mentions? = Mentions()

        when (relatesTo) {
            is RelatesTo.Replace -> {
                val oldMentions = roomService.getTimelineEventWithContentAndTimeout(roomId, relatesTo.eventId)
                    .content?.getOrNull()?.let {
                        if (it is MessageEventContent) it.mentions else null
                    }
                if (mentions != null) {
                    addedMentions = Mentions(
                        users = (mentions.users.orEmpty() - oldMentions?.users.orEmpty()).ifEmpty { null },
                        room = if (mentions.room == oldMentions?.room) null else mentions.room
                    )
                    newContentMentions = mentions
                }
            }

            else -> {}
        }
        addedMentions = Mentions(users = addedMentions?.users?.let { it - ownUserId }, room = addedMentions?.room)
        newContentMentions =
            Mentions(users = newContentMentions?.users?.let { it - ownUserId }, room = newContentMentions?.room)
        return contentBuilder(ContentBuilderInfo(relatesTo, addedMentions, newContentMentions))
    }
}

data class RoomMessageBuilderInfo(
    val body: String,
    val format: String?,
    val formattedBody: String?,
    val relatesTo: RelatesTo?,
    val mentions: Mentions?
)

fun MessageBuilder.roomMessageBuilder(
    body: String,
    format: String?,
    formattedBody: String?,
    builder: RoomMessageBuilderInfo.() -> RoomMessageEventContent,
) {
    contentBuilder = {
        when (relatesTo) {
            is RelatesTo.Replace -> builder(
                RoomMessageBuilderInfo(
                    body = "* $body",
                    format = format,
                    formattedBody = formattedBody?.let { "* $it" },
                    relatesTo = relatesTo.copy(
                        newContent = builder(
                            RoomMessageBuilderInfo(
                                body = body,
                                format = format,
                                formattedBody = formattedBody,
                                relatesTo = null,
                                mentions = newContentMentions,
                            )
                        )
                    ),
                    mentions = mentions,
                )
            )

            is RelatesTo.Reply, is RelatesTo.Thread -> {
                builder(
                    RoomMessageBuilderInfo(
                        body = body,
                        format = "org.matrix.custom.html",
                        formattedBody = formattedBody,
                        relatesTo = relatesTo,
                        mentions = mentions,
                    )
                )
            }

            else -> builder(
                RoomMessageBuilderInfo(
                    body = body,
                    format = format,
                    formattedBody = formattedBody,
                    relatesTo = relatesTo,
                    mentions = mentions,
                )
            )
        }
    }
}