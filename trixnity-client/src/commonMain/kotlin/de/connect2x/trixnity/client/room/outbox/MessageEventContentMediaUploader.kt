package de.connect2x.trixnity.client.room.outbox

import kotlinx.coroutines.flow.MutableStateFlow
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.MessageEventContent

fun interface MessageEventContentMediaUploader {
    suspend operator fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): MessageEventContent
}
