package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.serialization.m.room.message.MessageEventContentSerializer

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-message">matrix spec</a>
 */
@Serializable(with = MessageEventContentSerializer::class)
sealed class MessageEventContent : RoomEventContent {
    abstract val body: String

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-notice">matrix spec</a>
     */
    @Serializable
    data class NoticeMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : MessageEventContent() {
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
    ) : MessageEventContent() {
        companion object {
            const val type = "m.text"
        }
    }

    @Serializable
    data class UnknownMessageEventContent(
        @SerialName("msgtype") val type: String,
        @SerialName("body") override val body: String
    ) : MessageEventContent()
}

