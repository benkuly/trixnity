package net.folivo.trixnity.client.room.message

import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.eventId
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
fun MessageBuilder.replace(
    event: TimelineEvent,
) = replace(event.eventId)

@TrixnityDsl
fun MessageBuilder.replace(
    event: MessageEvent<*>,
) = replace(event.id)

@TrixnityDsl
fun MessageBuilder.replace(
    eventId: EventId,
) {
    relatesTo = RelatesTo.Replace(eventId, null)
}