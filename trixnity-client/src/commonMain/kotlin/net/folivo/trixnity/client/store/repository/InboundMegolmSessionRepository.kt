package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

interface InboundMegolmSessionRepository :
    FullRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession> {
    override fun serializeKey(key: InboundMegolmSessionRepositoryKey): String =
        key.roomId.full + key.sessionId

    suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession>
}

data class InboundMegolmSessionRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
)