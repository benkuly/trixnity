package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

typealias RoomAccountDataRepository = TwoDimensionsStoreRepository<RoomAccountDataRepositoryKey, String, Event.RoomAccountDataEvent<*>>

data class RoomAccountDataRepositoryKey(
    val roomId: RoomId,
    val type: String,
)