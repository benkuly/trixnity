package net.folivo.trixnity.client.room.outbox

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent

private val log = KotlinLogging.logger("net.folivo.trixnity.client.room.outbox.FallbackOutboxMessageMediaUploaderMapping")

val FallbackOutboxMessageMediaUploaderMapping =
    OutboxMessageMediaUploaderMapping(MessageEventContent::class, FallbackOutboxMessageMediaUploaderMappingClass())

class FallbackOutboxMessageMediaUploaderMappingClass() : MessageEventContentMediaUploader {
    override suspend fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (String, MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent {
        log.trace { "EventContent class ${content::class.simpleName} is not supported by any other media uploader." }
        return content
    }
}