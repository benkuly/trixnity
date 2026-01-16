package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession

interface OutboundMegolmSessionRepository : FullRepository<RoomId, StoredOutboundMegolmSession> {
    override fun serializeKey(key: RoomId): String = key.full
}