package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo

// TODO this has MSC-1767 in mind.
class MessageBuilder(val roomId: RoomId, val roomService: RoomService, val mediaService: MediaService) {
    var contentBuilder: suspend (RelatesTo?) -> MessageEventContent? = { null }
    var relatesTo: RelatesTo? = null

    suspend fun build(builder: suspend MessageBuilder.() -> Unit): MessageEventContent? {
        builder()
        return contentBuilder(relatesTo)
    }
}