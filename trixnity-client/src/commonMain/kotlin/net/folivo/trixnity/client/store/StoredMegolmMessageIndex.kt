package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.Key

data class StoredMegolmMessageIndex(
    val roomId: MatrixId.RoomId,
    val sessionId: String,
    val senderKey: Key.Curve25519Key,
    val messageIndex: Long,
    val eventId: MatrixId.EventId,
    val originTimestamp: Long
)