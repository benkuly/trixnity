package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType

@Serializable
data class TimelineEventRelation(
    val roomId: RoomId,
    val eventId: EventId,
    val relationType: RelationType,
    val relatedEventId: EventId,
)