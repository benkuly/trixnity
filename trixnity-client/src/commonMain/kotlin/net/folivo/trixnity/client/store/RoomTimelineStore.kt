package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomTimelineStore(
    private val roomTimelineRepository: MinimalStoreRepository<RoomTimelineKey, TimelineEvent>,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope,
) {
    private val roomTimelineCache = RepositoryStateFlowCache(storeScope, roomTimelineRepository, rtm)

    suspend fun deleteAll() {
        rtm.transaction {
            roomTimelineRepository.deleteAll()
        }
        roomTimelineCache.reset()
    }

    fun resetCache() {
        roomTimelineCache.reset()
    }

    suspend fun get(eventId: EventId, roomId: RoomId, scope: CoroutineScope): StateFlow<TimelineEvent?> =
        roomTimelineCache.get(RoomTimelineKey(eventId, roomId), scope = scope)

    suspend fun get(eventId: EventId, roomId: RoomId): TimelineEvent? =
        roomTimelineCache.get(RoomTimelineKey(eventId, roomId))

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        persistIntoRepository: Boolean = true,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = roomTimelineCache.update(
        RoomTimelineKey(eventId, roomId),
        persistIntoRepository,
        updater = updater
    )

    suspend fun addAll(events: List<TimelineEvent>) {
        events.forEach { event ->
            roomTimelineCache.update(
                RoomTimelineKey(event.eventId, event.roomId),
            ) { event }
        }
    }
}