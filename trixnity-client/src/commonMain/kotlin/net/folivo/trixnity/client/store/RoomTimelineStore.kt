package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MapDeleteByRoomIdRepositoryCoroutineCache
import net.folivo.trixnity.client.store.cache.MapRepositoryCoroutinesCacheKey
import net.folivo.trixnity.client.store.cache.MinimalDeleteByRoomIdRepositoryCoroutineCache
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType

class RoomTimelineStore(
    timelineEventRepository: TimelineEventRepository,
    timelineEventRelationRepository: TimelineEventRelationRepository,
    tm: TransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val timelineEventCache = MinimalDeleteByRoomIdRepositoryCoroutineCache(
        timelineEventRepository,
        tm,
        storeScope,
        config.cacheExpireDurations.timelineEvent
    ) { it.roomId }
    private val timelineEventRelationCache =
        MapDeleteByRoomIdRepositoryCoroutineCache(
            timelineEventRelationRepository,
            tm,
            storeScope,
            config.cacheExpireDurations.timelineEventRelation
        ) { it.firstKey.roomId }


    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        timelineEventCache.deleteAll()
        timelineEventRelationCache.deleteAll()
    }

    suspend fun deleteByRoomId(roomId: RoomId) {
        timelineEventCache.deleteByRoomId(roomId)
        timelineEventRelationCache.deleteByRoomId(roomId)
    }

    fun get(eventId: EventId, roomId: RoomId): Flow<TimelineEvent?> =
        timelineEventCache.read(TimelineEventKey(eventId, roomId))

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        persistIntoRepository: Boolean = true,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = timelineEventCache.write(
        TimelineEventKey(eventId, roomId),
        persistIntoRepository,
        updater = updater
    )

    suspend fun addAll(events: List<TimelineEvent>) {
        events.forEach { event ->
            timelineEventCache.write(TimelineEventKey(event.eventId, event.roomId), event)
        }
    }

    fun getRelations(
        relatedEventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Map<EventId, Flow<TimelineEventRelation?>>?> =
        timelineEventRelationCache.readByFirstKey(
            TimelineEventRelationKey(relatedEventId, roomId, relationType)
        )

    suspend fun addRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.write(
            MapRepositoryCoroutinesCacheKey(
                TimelineEventRelationKey(relation.relatedEventId, relation.roomId, relation.relationType),
                relation.eventId
            )
        ) {
            relation
        }
    }

    suspend fun deleteRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.write(
            MapRepositoryCoroutinesCacheKey(
                TimelineEventRelationKey(relation.relatedEventId, relation.roomId, relation.relationType),
                relation.eventId
            )
        ) {
            null
        }
    }
}