package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

val defaultOutboxMessageMediaUploaderMappings = OutboxMessageMediaUploaderMappings(
    listOf(
        of<RoomMessageEventContent.FileBased.File>(::fileRoomMessageEventContentMediaUploader),
        of<RoomMessageEventContent.FileBased.Image>(::imageRoomMessageEventContentMediaUploader),
        of<RoomMessageEventContent.FileBased.Video>(::videoRoomMessageEventContentMediaUploader),
        of<RoomMessageEventContent.FileBased.Audio>(::audioRoomMessageEventContentMediaUploader),
        FallbackOutboxMessageMediaUploaderMapping,
    )
)