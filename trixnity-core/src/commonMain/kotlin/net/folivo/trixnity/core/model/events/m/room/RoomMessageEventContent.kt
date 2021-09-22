package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
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
        @SerialName("info") val format: ImageInfo? = null,
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
        @SerialName("info") val format: FileInfo? = null,
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
        @SerialName("info") val format: AudioInfo? = null,
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
        @SerialName("info") val format: VideoInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent {
        companion object {
            const val type = "m.video"
        }
    }

    @Serializable
    data class UnknownMessageEventContent(
        @SerialName("msgtype") val type: String,
        @SerialName("body") override val body: String
    ) : RoomMessageEventContent
}

