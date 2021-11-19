package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.crypto.Key

typealias InboundMegolmSessionRepository =
        MinimalStoreRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>

data class InboundMegolmSessionRepositoryKey(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: RoomId,
)