package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo

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