package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent
import net.folivo.trixnity.core.serialization.MessageEventContentSerializer

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-message">matrix spec</a>
 */
@Serializable
data class MessageEvent<C : MessageEventContent>(
    @SerialName("content") override val content: C,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("type") override val type: String = "m.room.message"
) : RoomEvent<MessageEventContent> {

    @Serializable(with = MessageEventContentSerializer::class)
    sealed class MessageEventContent {
        abstract val body: String
        abstract val messageType: String

        /**
         * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-notice">matrix spec</a>
         */
        @Serializable
        data class NoticeMessageEventContent(
            @SerialName("body") override val body: String,
            @SerialName("format") val format: String? = null,
            @SerialName("formatted_body") val formattedBody: String? = null,
            @SerialName("msgtype") override val messageType: String = "m.notice"
        ) : MessageEventContent()

        /**
         * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-text">matrix spec</a>
         */
        @Serializable
        data class TextMessageEventContent(
            @SerialName("body") override val body: String,
            @SerialName("format") val format: String? = null,
            @SerialName("formatted_body") val formattedBody: String? = null,
            @SerialName("msgtype") override val messageType: String = "m.text"
        ) : MessageEventContent()

        @Serializable
        data class UnknownMessageEventContent(
            @SerialName("body") override val body: String,
            @SerialName("msgtype") override val messageType: String
        ) : MessageEventContent()
    }
}

