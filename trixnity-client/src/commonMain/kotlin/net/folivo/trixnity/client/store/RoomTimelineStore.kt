package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.cache.MinimalRepositoryStateFlowCache
import net.folivo.trixnity.client.store.cache.TwoDimensionsRepositoryStateFlowCache
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationKey
import net.folivo.trixnity.client.store.repository.TimelineEventRelationRepository
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.client.store.transaction.TransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

class RoomTimelineStore(
    private val timelineEventRepository: TimelineEventRepository,
    private val timelineEventRelationRepository: TimelineEventRelationRepository,
    private val tm: TransactionManager,
    config: MatrixClientConfiguration,
    storeScope: CoroutineScope,
) : Store {
    private val timelineEventCache = MinimalRepositoryStateFlowCache(
        storeScope,
        timelineEventRepository,
        tm,
        config.cacheExpireDurations.timelineEvent
    )
    private val timelineEventRelationCache =
        TwoDimensionsRepositoryStateFlowCache(
            storeScope,
            timelineEventRelationRepository,
            tm,
            config.cacheExpireDurations.timelineEventRelation
        )


    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        tm.writeOperation {
            timelineEventRepository.deleteAll()
            timelineEventRelationRepository.deleteAll()
        }
        timelineEventCache.reset()
        timelineEventRelationCache.reset()
    }

    fun get(eventId: EventId, roomId: RoomId): Flow<TimelineEvent?> =
        timelineEventCache.get(TimelineEventKey(eventId, roomId))

    suspend fun update(
        eventId: EventId,
        roomId: RoomId,
        persistIntoRepository: Boolean = true,
        updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
    ) = timelineEventCache.update(
        TimelineEventKey(eventId, roomId),
        persistIntoRepository,
        updater = updater
    )

    suspend fun addAll(events: List<TimelineEvent>) {
        events.forEach { event ->
            timelineEventCache.save(TimelineEventKey(event.eventId, event.roomId), event)
        }
    }

    fun getRelations(
        eventId: EventId,
        roomId: RoomId,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?> =
        timelineEventRelationCache.get(TimelineEventRelationKey(eventId, roomId))

    fun getRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Set<TimelineEventRelation>?> =
        timelineEventRelationCache.getBySecondKey(
            TimelineEventRelationKey(eventId, roomId),
            relationType,
        )

    suspend fun addRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.updateBySecondKey(
            TimelineEventRelationKey(relation.relatedEventId, relation.roomId),
            relation.relationType
        ) {
            it.orEmpty() + relation
        }
    }

    suspend fun deleteRelation(relation: TimelineEventRelation) {
        timelineEventRelationCache.updateBySecondKey(
            TimelineEventRelationKey(relation.relatedEventId, relation.roomId),
            relation.relationType
        ) {
            it?.minus(relation)?.ifEmpty { null }
        }
    }
}