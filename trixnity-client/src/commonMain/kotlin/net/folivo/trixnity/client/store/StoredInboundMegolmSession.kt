package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.crypto.Key

data class StoredInboundMegolmSession(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: RoomId,
    val pickle: String
)