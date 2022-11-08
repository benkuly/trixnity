package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo

/**
 * [event] must be the last known event of a thread.
 */
@TrixnityDsl
fun MessageBuilder.thread(
    event: TimelineEvent,
    reply: Boolean = false,
) {
    val replyTo = RelatesTo.ReplyTo(event.eventId)
    val eventRelatesTo = event.relatesTo
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, reply.not())
        } else {
            RelatesTo.Thread(event.eventId, replyTo, reply.not())
        }
}