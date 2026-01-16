package de.connect2x.trixnity.client.room.message

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.relatesTo
import de.connect2x.trixnity.client.store.sender
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo

suspend fun MessageBuilder.reply(
    event: TimelineEvent,
) = reply(event.eventId, event.relatesTo)

suspend fun MessageBuilder.reply(
    event: MessageEvent<*>,
) = reply(event.id, event.content.relatesTo)

/**
 * Important: [eventRelatesTo] should be set from the event, that is replied. Otherwise, thread support is dropped.
 */
suspend fun MessageBuilder.reply(
    eventId: EventId,
    eventRelatesTo: RelatesTo?,
) {
    val replyTo = RelatesTo.ReplyTo(eventId)
    val repliedTimelineEvent = roomService.getTimelineEventWithContentAndTimeout(roomId, replyTo.eventId)
    mentions = Mentions(setOfNotNull(repliedTimelineEvent.sender)) + (mentions ?: Mentions())
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, true)
        } else {
            RelatesTo.Reply(replyTo)
        }
}