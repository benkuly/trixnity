package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

interface TimelineEventRelationRepository :
    MapDeleteByRoomIdRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>> {
    override fun serializeKey(key: TimelineEventRelationKey): String =
        this::class.simpleName + key.roomId.full + key.relatedEventId.full

    override fun serializeKey(firstKey: TimelineEventRelationKey, secondKey: RelationType): String =
        serializeKey(firstKey) + secondKey.name
}

data class TimelineEventRelationKey(
    val relatedEventId: EventId,
    val roomId: RoomId,
)