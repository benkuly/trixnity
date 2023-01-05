package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
) : Store {
    private val timelineEventCache = RepositoryStateFlowCache(storeScope, timelineEventRepository, rtm)
    private val timelineEventRelationCache =
        TwoDimensionsRepositoryStateFlowCache(storeScope, timelineEventRelationRepository, rtm)


    override suspend fun init() {}

    override suspend fun clearCache() = deleteAll()
    override suspend fun deleteAll() {
        rtm.writeTransaction {
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
            timelineEventCache.update(
                TimelineEventKey(event.eventId, event.roomId)
            ) { event }
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