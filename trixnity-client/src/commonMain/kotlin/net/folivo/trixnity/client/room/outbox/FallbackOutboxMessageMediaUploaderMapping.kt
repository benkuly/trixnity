package net.folivo.trixnity.client.room.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

private val log = KotlinLogging.logger { }

val FallbackOutboxMessageMediaUploaderMapping =
    OutboxMessageMediaUploaderMapping(MessageEventContent::class, FallbackOutboxMessageMediaUploaderMappingClass())

class FallbackOutboxMessageMediaUploaderMappingClass() : RoomMessageEventContentMediaUploader {
    override val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)

    override suspend fun uploader(
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent {
        log.trace { "EventContent class ${content::class.simpleName} is not supported by any other media uploader." }
        return content as RoomMessageEventContent
    }
}