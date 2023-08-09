package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
fun MessageBuilder.react(
    event: TimelineEvent,
    key: String,
) = react(event.eventId, key)

@TrixnityDsl
fun MessageBuilder.react(
    event: Event.MessageEvent<*>,
    key: String,
) = react(event.id, key)

@TrixnityDsl
fun MessageBuilder.react(
    eventId: EventId,
    key: String
) {
    contentBuilder = { _, _, _ ->
        ReactionEventContent(RelatesTo.Annotation(eventId, key))
    }
}