package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

val defaultOutboxMessageMediaUploaderMappings = OutboxMessageMediaUploaderMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(FileRoomMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Image>(ImageRoomMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Video>(VideoRoomMessageEventContentMediaUploader()),
        of<RoomMessageEventContent.FileBased.Audio>(AudioRoomMessageEventContentMediaUploader()),
        FallbackOutboxMessageMediaUploaderMapping,
    )
)