package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.Key

typealias InboundMegolmMessageIndexRepository =
        MinimalStoreRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>

data class InboundMegolmMessageIndexRepositoryKey(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: MatrixId.RoomId,
    val messageIndex: Long
)