package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable
data class RoomOutboxMessage<T : MessageEventContent>(
    val transactionId: String,
    val roomId: RoomId,
    val content: T,
    val sentAt: Instant? = null,
    val sendError: SendError? = null,
    val keepMediaInCache: Boolean = true,
) {
    @Transient
    val mediaUploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonClassDiscriminator("type")
    sealed interface SendError {
        /**
         * The user has no permission to send this event in this room.
         */
        @Serializable
        object NoEventPermission : SendError

        /**
         * The user has no permission to send this media (for example file type not allowed or quota reached).
         */
        @Serializable
        object NoMediaPermission : SendError

        /**
         * The media tried to upload is too large.
         */
        @Serializable
        object MediaTooLarge : SendError

        /**
         * The event tried to send is invalid.
         */
        @Serializable // FIXME test, that de/seri works.
        data class BadRequest(
            val errorResponse: @Serializable(with = ErrorResponseSerializer::class) ErrorResponse
        ) : SendError

        /**
         * The [EventContent] is not supported. You must register a media uploader for it.
         */
        @Serializable // FIXME test, that de/seri works.
        data class Unknown(
            val errorResponse: @Serializable(with = ErrorResponseSerializer::class) ErrorResponse
        ) : SendError
    }
}