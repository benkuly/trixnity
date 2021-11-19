package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.StateFlowCache
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomTimelineStore(
    roomTimelineRepository: MinimalStoreRepository<RoomTimelineKey, TimelineEvent>,
    storeScope: CoroutineScope,
) {

    private val roomTimelineCache = StateFlowCache(storeScope, roomTimelineRepository)

    suspend fun get(eventId: EventId, roomId: RoomId, scope: CoroutineScope): StateFlow<TimelineEvent?> =
        roomTimelineCache.get(RoomTimelineKey(eventId, roomId), scope)

    suspend fun get(eventId: EventId, roomId: RoomId): TimelineEvent? =
        roomTimelineCache.get(RoomTimelineKey(eventId, roomId))

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = roomTimelineCache.update(RoomTimelineKey(eventId, roomId), updater)

    suspend fun addAll(events: List<TimelineEvent>) {
        events.forEach { event ->
            roomTimelineCache.update(RoomTimelineKey(event.eventId, event.roomId)) { event }
        }
    }
}