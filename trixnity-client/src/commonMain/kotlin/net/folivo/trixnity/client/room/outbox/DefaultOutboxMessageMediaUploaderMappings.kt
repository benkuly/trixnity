package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

val DefaultOutboxMessageMediaUploaderMappings = setOf(
    of<RoomMessageEventContent>(::roomMessageEventContentMediaUploader)
)