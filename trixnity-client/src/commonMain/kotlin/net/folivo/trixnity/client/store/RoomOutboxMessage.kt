package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.media.FileTransferProgress
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent

data class RoomOutboxMessage(
    val content: MessageEventContent,
    val roomId: RoomId,
    val transactionId: String,
    val wasSent: Boolean,
    // this field should not be saved in database
    val mediaUploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)
)