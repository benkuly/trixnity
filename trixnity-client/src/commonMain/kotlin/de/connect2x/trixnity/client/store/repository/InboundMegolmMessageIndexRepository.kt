package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

interface InboundMegolmMessageIndexRepository :
    MinimalRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex> {
    override fun serializeKey(key: InboundMegolmMessageIndexRepositoryKey): String =
        key.roomId.full + key.sessionId + key.messageIndex
}

data class InboundMegolmMessageIndexRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long
)