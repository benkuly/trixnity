package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.RelatesTo

@TrixnityDsl
fun MessageBuilder.replace(
    eventId: EventId,
) {
    relatesTo = RelatesTo.Replace(eventId, null)
}