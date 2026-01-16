package de.connect2x.trixnity.client.room.outbox

import de.connect2x.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent

private val outboxMessageMediaUploaderMappingsDefault = OutboxMessageMediaUploaderMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(FileMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Image>(ImageMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Video>(VideoMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Audio>(AudioMessageEventContentMediaUploader()),
        FallbackOutboxMessageMediaUploaderMapping,
    )
)

val OutboxMessageMediaUploaderMappings.Companion.default get() = outboxMessageMediaUploaderMappingsDefault