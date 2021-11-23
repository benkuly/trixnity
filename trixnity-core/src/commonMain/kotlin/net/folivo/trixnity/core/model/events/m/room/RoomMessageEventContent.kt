package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequest
import net.folivo.trixnity.core.serialization.m.room.message.RoomMessageEventContentSerializer

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-message">matrix spec</a>
 */
@Serializable(with = RoomMessageEventContentSerializer::class)
sealed interface RoomMessageEventContent : MessageEventContent {
    val body: String

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-notice">matrix spec</a>
     */
    @Serializable
    data class NoticeMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.notice"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-text">matrix spec</a>
     */
    @Serializable
    data class TextMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.text"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-emote">matrix spec</a>
     */
    @Serializable
    data class EmoteMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.emote"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-image">matrix spec</a>
     */
    @Serializable
    data class ImageMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: ImageInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.image"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-file">matrix spec</a>
     */
    @Serializable
    data class FileMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("filename") val fileName: String? = null,
        @SerialName("info") val info: FileInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.file"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-audio">matrix spec</a>
     */
    @Serializable
    data class AudioMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: AudioInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.audio"
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-video">matrix spec</a>
     */
    @Serializable
    data class VideoMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: VideoInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.video"
        }
    }

    @Serializable
    data class VerificationRequestMessageEventContent(
        @SerialName("from_device") override val fromDevice: String,
        @SerialName("to") val to: UserId,
        @SerialName("methods") override val methods: Set<VerificationMethod>,
        @SerialName("body") override val body: String = "Attempting verification request. (m.key.verification.request) Apparently your client doesn't support this.",
    ) : RoomMessageEventContent, VerificationRequest {
        companion object {
            const val type = "m.key.verification.request"
        }
    }

    data class UnknownMessageEventContent(
        val type: String,
        override val body: String,
        val raw: JsonObject
    ) : RoomMessageEventContent
}

