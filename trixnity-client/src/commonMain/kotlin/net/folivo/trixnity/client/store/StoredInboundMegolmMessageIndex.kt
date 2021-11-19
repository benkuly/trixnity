package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.crypto.Key

data class StoredInboundMegolmMessageIndex(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long,
    val eventId: EventId,
    val originTimestamp: Long
)