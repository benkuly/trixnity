package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.cache.RepositoryStateFlowCache
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

class RoomTimelineStore(
    private val timelineEventRepository: TimelineEventRepository,
    private val timelineEventRelationRepository: TimelineEventRelationRepository,
    private val rtm: RepositoryTransactionManager,
    storeScope: CoroutineScope,
) : IStore {
    private val timelineEventCache = RepositoryStateFlowCache(storeScope, timelineEventRepository, rtm)
    private val timelineEventRelationCache =
        TwoDimensionsRepositoryStateFlowCache(storeScope, timelineEventRelationRepository, rtm)


    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        rtm.transaction {
            timelineEventRepository.deleteAll()
            timelineEventRelationRepository.deleteAll()
        }
        timelineEventCache.reset()
        timelineEventRelationCache.reset()
    }

    suspend fun get(eventId: EventId, roomId: RoomId, scope: CoroutineScope): StateFlow<TimelineEvent?> =
        timelineEventCache.get(TimelineEventKey(eventId, roomId), scope = scope)

    suspend fun get(eventId: EventId, roomId: RoomId, withTransaction: Boolean = true): TimelineEvent? =
        timelineEventCache.get(TimelineEventKey(eventId, roomId), withTransaction = withTransaction)

    suspend fun getRelations(
        eventId: EventId,
        roomId: RoomId,
        scope: CoroutineScope
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?> =
        timelineEventRelationCache.get(TimelineEventRelationKey(eventId, roomId), scope = scope)

    suspend fun getRelations(
        eventId: EventId,
        roomId: RoomId,
    ): Map<RelationType, Set<TimelineEventRelation>?>? =
        timelineEventRelationCache.get(TimelineEventRelationKey(eventId, roomId))

    suspend fun getRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
        scope: CoroutineScope
    ): Flow<Set<TimelineEventRelation>?> =
        timelineEventRelationCache.getBySecondKey(
            TimelineEventRelationKey(eventId, roomId),
            relationType,
            scope = scope
        )

    suspend fun getRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType
    ): Set<TimelineEventRelation>? =
        timelineEventRelationCache.getBySecondKey(TimelineEventRelationKey(eventId, roomId), relationType)

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        withTransaction: Boolean = true,
        persistIntoRepository: Boolean = true,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = timelineEventCache.update(
        TimelineEventKey(eventId, roomId),
        persistIntoRepository,
        withTransaction = withTransaction,
        updater = updater
    )

    suspend fun addAll(events: List<TimelineEvent>, withTransaction: Boolean = true) {
        events.forEach { event ->
            timelineEventCache.update(
                TimelineEventKey(event.eventId, event.roomId), withTransaction = withTransaction
            ) { event }
        }
    }

    suspend fun addRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.updateBySecondKey(
            TimelineEventRelationKey(relation.relatedEventId, relation.roomId),
            relation.relationType
        ) {
            it.orEmpty() + relation
        }
    }
}