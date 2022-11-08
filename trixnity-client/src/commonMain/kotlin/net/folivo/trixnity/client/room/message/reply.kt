package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.relatesTo
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.RelatesTo

@TrixnityDsl
fun MessageBuilder.reply(
    event: TimelineEvent,
) {
    val replyTo = RelatesTo.ReplyTo(event.eventId)
    val eventRelatesTo = event.relatesTo
    relatesTo =
        if (eventRelatesTo is RelatesTo.Thread) {
            RelatesTo.Thread(eventRelatesTo.eventId, replyTo, true)
        } else {
            RelatesTo.Reply(replyTo)
        }
}