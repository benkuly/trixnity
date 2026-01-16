package de.connect2x.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

@Serializable
data class StoredInboundMegolmMessageIndex(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long,
    val eventId: EventId,
    val originTimestamp: Long
)