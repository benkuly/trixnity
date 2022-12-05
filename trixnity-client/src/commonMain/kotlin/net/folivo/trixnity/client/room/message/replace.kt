package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelatesTo

@TrixnityDsl
fun MessageBuilder.replace(
    event: TimelineEvent,
) = replace(event.eventId)

@TrixnityDsl
fun MessageBuilder.replace(
    event: Event.MessageEvent<*>,
) = replace(event.id)

@TrixnityDsl
fun MessageBuilder.replace(
    eventId: EventId,
) {
    relatesTo = RelatesTo.Replace(eventId, null)
}