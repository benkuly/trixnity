package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

interface InboundMegolmMessageIndexRepository :
    MinimalRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex> {
    override fun serializeKey(key: InboundMegolmMessageIndexRepositoryKey): String =
        this::class.simpleName + key.roomId.full + key.sessionId + key.messageIndex
}

data class InboundMegolmMessageIndexRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
    val messageIndex: Long
)