package net.folivo.trixnity.client.room.outbox

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent

fun interface RoomMessageEventContentMediaUploader {
    suspend operator fun invoke(
        uploadProgress: MutableStateFlow<FileTransferProgress?>,
        content: MessageEventContent,
        upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
    ): RoomMessageEventContent
}
