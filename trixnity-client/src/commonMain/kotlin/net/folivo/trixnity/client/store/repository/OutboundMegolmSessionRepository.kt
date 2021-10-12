package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.core.model.MatrixId

typealias OutboundMegolmSessionRepository = MinimalStoreRepository<MatrixId.RoomId, StoredOutboundMegolmSession>