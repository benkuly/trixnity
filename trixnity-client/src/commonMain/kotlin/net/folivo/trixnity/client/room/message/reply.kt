package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelatesTo

@TrixnityDsl
fun MessageBuilder.reply(
    event: TimelineEvent,
) = reply(event.eventId, event.relatesTo)

@TrixnityDsl
fun MessageBuilder.reply(
    event: Event.MessageEvent<*>,
) = reply(event.id, event.content.relatesTo)

/**
 * Important: [eventRelatesTo] should be set from the event, that is replied. Otherwise, thread support is dropped.
 */
@TrixnityDsl
fun MessageBuilder.reply(
    eventId: EventId,
    eventRelatesTo: RelatesTo?,
) {
    val replyTo = RelatesTo.ReplyTo(eventId)
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, true)
        } else {
            RelatesTo.Reply(replyTo)
        }
}