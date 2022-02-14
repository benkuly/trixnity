package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.client.api.model.media.FileTransferProgress
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable
data class RoomOutboxMessage<T : MessageEventContent>(
    val transactionId: String,
    val roomId: RoomId,
    val content: T,
    val sentAt: Instant? = null,
    val retryCount: Int = 0,
    @Transient
    val mediaUploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)
) {
    companion object {
        const val MAX_RETRY_COUNT = 3
    }

    val reachedMaxRetryCount: Boolean
        get() = retryCount >= MAX_RETRY_COUNT
}