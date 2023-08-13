package net.folivo.trixnity.client.room.outbox

import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping.Companion.of
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

val defaultOutboxMessageMediaUploaderMappings = OutboxMessageMediaUploaderMappings(
    listOf(
        of<RoomMessageEventContent.FileMessageEventContent>(::fileMessageEventContentMediaUploader),
        of<RoomMessageEventContent.ImageMessageEventContent>(::imageMessageEventContentMediaUploader),
        of<RoomMessageEventContent.VideoMessageEventContent>(::videoMessageEventContentMediaUploader),
        of<RoomMessageEventContent.AudioMessageEventContent>(::audioMessageEventContentMediaUploader),
        // fallback
        of<MessageEventContent> { content, _ -> content },
    )
)