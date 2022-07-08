package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

interface InboundMegolmSessionRepository :
    MinimalStoreRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession> {
    suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession>
}

data class InboundMegolmSessionRepositoryKey(
    val sessionId: String,
    val roomId: RoomId,
)