package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo

// TODO this has MSC-1767 in mind. So if it land, we could stay backward compatible by making content an set
class MessageBuilder(val isEncryptedRoom: Boolean, val mediaService: MediaService) {
    var contentBuilder: (RelatesTo?) -> MessageEventContent? = { null }
    var relatesTo: RelatesTo? = null

    internal suspend fun build(builder: suspend MessageBuilder.() -> Unit): MessageEventContent? {
        builder()
        return contentBuilder(relatesTo)
    }
}