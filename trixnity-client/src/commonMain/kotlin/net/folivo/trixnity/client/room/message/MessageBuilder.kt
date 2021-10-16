package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.media.MediaManager
import net.folivo.trixnity.core.model.events.MessageEventContent

// TODO this has MSC-1767 in mind. So if it land, we could stay backward compatible by making content an set
class MessageBuilder(val isEncryptedRoom: Boolean, val mediaManager: MediaManager) {
    var content: MessageEventContent? = null

    internal suspend fun build(builder: suspend MessageBuilder.() -> Unit): MessageEventContent? {
        builder()
        return content
    }
}