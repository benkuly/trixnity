package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomTimelineStore(
    roomTimelineRepository: MinimalStoreRepository<RoomTimelineKey, TimelineEvent>,
    rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope,
) {
    private val roomTimelineCache = RepositoryStateFlowCache(storeScope, roomTimelineRepository, rtm)

    fun resetCache() {
        roomTimelineCache.reset()
    }

    suspend fun get(eventId: EventId, roomId: RoomId, scope: CoroutineScope): StateFlow<TimelineEvent?> =
        roomTimelineCache.get(RoomTimelineKey(eventId, roomId), scope)

    suspend fun get(eventId: EventId, roomId: RoomId, withTransaction: Boolean = true): TimelineEvent? =
        roomTimelineCache.get(
            RoomTimelineKey(eventId, roomId),
            withTransaction = withTransaction,
        )

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        persistIntoRepository: Boolean = true,
        withTransaction: Boolean = true,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = roomTimelineCache.update(
        RoomTimelineKey(eventId, roomId),
        persistIntoRepository,
        withTransaction = withTransaction,
        updater = updater
    )

    suspend fun addAll(events: List<TimelineEvent>, withTransaction: Boolean = true) {
        events.forEach { event ->
            roomTimelineCache.update(
                RoomTimelineKey(event.eventId, event.roomId),
                withTransaction = withTransaction
            ) { event }
        }
    }
}