package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.core.model.RoomId

typealias InboundMegolmMessageIndexRepository =
        MinimalStoreRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>

data class InboundMegolmMessageIndexRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long
)