package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event

typealias RoomAccountDataRepository = MinimalStoreRepository<RoomAccountDataRepositoryKey, Event.RoomAccountDataEvent<*>>

data class RoomAccountDataRepositoryKey(
    val roomId: RoomId,
    val type: String,
)