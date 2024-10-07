package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable
data class RoomOutboxMessage<T : MessageEventContent>(
    val transactionId: String,
    val roomId: RoomId,
    val content: T,
    val sentAt: Instant? = null,
    val eventId: EventId? = null,
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
        @SerialName("no_event_permission")
        data object NoEventPermission : SendError

        /**
         * The user has no permission to send this media (for example file type not allowed or quota reached).
         */
        @Serializable
        @SerialName("no_media_permission")
        data object NoMediaPermission : SendError

        /**
         * The media tried to upload is too large.
         */
        @SerialName("media_too_large")
        @Serializable
        data object MediaTooLarge : SendError

        /**
         * The event tried to send is invalid.
         */
        @Serializable
        @SerialName("bad_request")
        data class BadRequest(
            val errorResponse: @Serializable(with = ErrorResponseSerializer::class) ErrorResponse
        ) : SendError

        /**
         * There was a failure in encrypting the event.
         */
        @Serializable
        @SerialName("encryption_error")
        data class EncryptionError(val reason: String? = null) : SendError

        /**
         * The encryption algorithm is not supported.
         */
        @Serializable
        @SerialName("encryption_algorithm_not_supported")
        data object EncryptionAlgorithmNotSupported : SendError

        @Serializable
        @SerialName("unknown")
        data class Unknown(
            val errorResponse: @Serializable(with = ErrorResponseSerializer::class) ErrorResponse? = null,
            val message: String? = null,
        ) : SendError
    }
}