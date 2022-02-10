package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

typealias RoomUserRepository = TwoDimensionsStoreRepository<RoomId, UserId, RoomUser>