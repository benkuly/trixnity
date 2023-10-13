package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.client.store.sender
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
suspend fun MessageBuilder.reply(
    event: TimelineEvent,
) = reply(event.eventId, event.relatesTo)

@TrixnityDsl
suspend fun MessageBuilder.reply(
    event: MessageEvent<*>,
) = reply(event.id, event.content.relatesTo)

/**
 * Important: [eventRelatesTo] should be set from the event, that is replied. Otherwise, thread support is dropped.
 */
@TrixnityDsl
suspend fun MessageBuilder.reply(
    eventId: EventId,
    eventRelatesTo: RelatesTo?,
) {
    val replyTo = RelatesTo.ReplyTo(eventId)
    val repliedTimelineEvent = roomService.getTimelineEventWithContentAndTimeout(roomId, replyTo.eventId)
    val repliedMentions = repliedTimelineEvent.content?.getOrNull()?.let {
        if (it is MessageEventContent) it.mentions else null
    }
    mentions = Mentions(setOf(repliedTimelineEvent.sender)) + repliedMentions + mentions
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, true)
        } else {
            RelatesTo.Reply(replyTo)
        }
}