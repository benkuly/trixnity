package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key

interface InboundMegolmSessionRepository :
    MinimalStoreRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession> {
    suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession>
}

data class InboundMegolmSessionRepositoryKey(
    val senderKey: Key.Curve25519Key,
    val sessionId: String,
    val roomId: RoomId,
)