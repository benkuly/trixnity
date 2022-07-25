package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

typealias InboundMegolmMessageIndexRepository =
        MinimalStoreRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>

data class InboundMegolmMessageIndexRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long
)