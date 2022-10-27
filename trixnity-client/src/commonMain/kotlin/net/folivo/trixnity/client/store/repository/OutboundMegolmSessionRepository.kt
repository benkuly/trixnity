package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

interface OutboundMegolmSessionRepository : MinimalStoreRepository<RoomId, StoredOutboundMegolmSession>