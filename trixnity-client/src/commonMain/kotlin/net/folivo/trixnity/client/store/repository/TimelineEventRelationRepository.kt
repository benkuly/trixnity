package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

interface TimelineEventRelationRepository :
    MapDeleteByRoomIdRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>> {

    override fun serializeKey(firstKey: TimelineEventRelationKey, secondKey: RelationType): String =
        firstKey.roomId.full + firstKey.relatedEventId.full + secondKey.name
}

data class TimelineEventRelationKey(
    val relatedEventId: EventId,
    val roomId: RoomId,
)