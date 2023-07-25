package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

// TODO this has MSC-1767 in mind.
class MessageBuilder(
    val roomId: RoomId,
    val roomService: RoomService,
    val mediaService: MediaService,
    val ownUserId: UserId
) {
    var contentBuilder: suspend (relatesTo: RelatesTo?, mentions: Mentions?, newContentMentions: Mentions?) -> MessageEventContent? =
        { _, _, _ -> null }
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
                val oldMentions = roomService.getTimelineEventWithTimedOutContent(roomId, relatesTo.eventId)
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
        return contentBuilder(relatesTo, addedMentions, newContentMentions)
    }
}