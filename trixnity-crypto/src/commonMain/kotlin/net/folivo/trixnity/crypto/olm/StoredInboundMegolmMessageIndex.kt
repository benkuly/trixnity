package net.folivo.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class StoredInboundMegolmMessageIndex(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long,
    val eventId: EventId,
    val originTimestamp: Long
)