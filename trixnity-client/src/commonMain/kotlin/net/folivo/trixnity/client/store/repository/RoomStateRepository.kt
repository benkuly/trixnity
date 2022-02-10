package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event


typealias RoomStateRepository = TwoDimensionsStoreRepository<RoomStateRepositoryKey, String, Event<*>>

data class RoomStateRepositoryKey(
    val roomId: RoomId,
    val type: String
)