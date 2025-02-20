package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

val defaultOutboxMessageMediaUploaderMappings = OutboxMessageMediaUploaderMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(FileMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Image>(ImageMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Video>(VideoMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Audio>(AudioMessageEventContentMediaUploader()),
        FallbackOutboxMessageMediaUploaderMapping,
    )
)