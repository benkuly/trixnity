package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

typealias TimelineEventRelationRepository = TwoDimensionsStoreRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>>

data class TimelineEventRelationKey(
    val relatedEventId: EventId,
    val roomId: RoomId,
)