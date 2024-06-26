package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo

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