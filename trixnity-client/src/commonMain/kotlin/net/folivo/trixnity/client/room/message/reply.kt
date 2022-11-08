package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.RelatesTo

@TrixnityDsl
fun MessageBuilder.reply(
    eventId: EventId,
) {
    relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(eventId))
}