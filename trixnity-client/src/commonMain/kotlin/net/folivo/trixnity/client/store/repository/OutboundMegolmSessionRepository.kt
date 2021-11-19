package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.core.model.RoomId

typealias OutboundMegolmSessionRepository = MinimalStoreRepository<RoomId, StoredOutboundMegolmSession>