package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.ReactionEventContent
import de.connect2x.trixnity.core.model.events.m.RelatesTo

fun MessageBuilder.react(
    event: TimelineEvent,
    key: String,
) = react(event.eventId, key)

fun MessageBuilder.react(
    event: MessageEvent<*>,
    key: String,
) = react(event.id, key)

fun MessageBuilder.react(
    eventId: EventId,
    key: String
) {
    content(ReactionEventContent(RelatesTo.Annotation(eventId, key)))
}