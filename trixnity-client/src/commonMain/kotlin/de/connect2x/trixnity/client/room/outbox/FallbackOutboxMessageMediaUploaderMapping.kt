package de.connect2x.trixnity.client.room.outbox

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.MessageEventContent

private val log = Logger("de.connect2x.trixnity.client.room.outbox.FallbackOutboxMessageMediaUploaderMapping")

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