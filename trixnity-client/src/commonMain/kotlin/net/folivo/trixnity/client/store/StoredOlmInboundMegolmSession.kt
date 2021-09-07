package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.Key

data class StoredOlmInboundMegolmSession(
    val sessionId: String,
    val senderKey: Key.Curve25519Key,
    val roomId: MatrixId.RoomId,
    val pickle: String
)