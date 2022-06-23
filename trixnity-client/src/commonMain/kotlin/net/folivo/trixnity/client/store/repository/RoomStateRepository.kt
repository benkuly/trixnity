package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent


typealias RoomStateRepository = TwoDimensionsStoreRepository<RoomStateRepositoryKey, String, ClientEvent<*>>

data class RoomStateRepositoryKey(
    val roomId: RoomId,
    val type: String
)