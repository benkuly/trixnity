package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.RelationType

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