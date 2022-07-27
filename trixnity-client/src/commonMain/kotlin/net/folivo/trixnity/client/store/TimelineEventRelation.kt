package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.RelationType

@Serializable
data class TimelineEventRelation(
    val roomId: RoomId,
    val eventId: EventId,
    val relationType: RelationType,
    val relatedEventId: EventId,
)