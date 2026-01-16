package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType

interface TimelineEventRelationRepository :
    DeleteByRoomIdMapRepository<TimelineEventRelationKey, EventId, TimelineEventRelation> {

    override fun serializeKey(firstKey: TimelineEventRelationKey, secondKey: EventId): String =
        firstKey.roomId.full + firstKey.relatedEventId.full + firstKey.relationType.name + secondKey.full
}

data class TimelineEventRelationKey(
    val relatedEventId: EventId,
    val roomId: RoomId,
    val relationType: RelationType,
)