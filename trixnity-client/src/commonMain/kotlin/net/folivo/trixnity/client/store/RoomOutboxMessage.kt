package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.media.FileTransferProgress
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent

data class RoomOutboxMessage(
    val transactionId: String,
    val roomId: RoomId,
    val content: MessageEventContent,
    val wasSent: Boolean,
    val mediaUploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)
)