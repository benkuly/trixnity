package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.RelatesTo

fun MessageBuilder.replace(
    event: TimelineEvent,
) = replace(event.eventId)

fun MessageBuilder.replace(
    event: MessageEvent<*>,
) = replace(event.id)

fun MessageBuilder.replace(
    eventId: EventId,
) {
    relatesTo = RelatesTo.Replace(eventId, null)
}