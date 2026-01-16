package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.RelatesTo

/**
 * [event] must be the last known event of a thread.
 */
fun MessageBuilder.thread(
    event: TimelineEvent,
    reply: Boolean = false,
) = thread(event.eventId, event.relatesTo, reply)

/**
 * [event] must be the last known event of a thread.
 */
fun MessageBuilder.thread(
    event: MessageEvent<*>,
    reply: Boolean = false,
) = thread(event.id, event.content.relatesTo, reply)

/**
 * [eventId] and [eventRelatesTo] must be from the last known event of a thread.
 */
fun MessageBuilder.thread(
    eventId: EventId,
    eventRelatesTo: RelatesTo? = null,
    reply: Boolean = false,
) {
    val replyTo = RelatesTo.ReplyTo(eventId)
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, reply.not())
        } else {
            RelatesTo.Thread(eventId, replyTo, reply.not())
        }
}