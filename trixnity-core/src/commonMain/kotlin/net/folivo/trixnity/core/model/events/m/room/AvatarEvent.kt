package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.AvatarEvent.AvatarEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-avatar">matrix spec</a>
 */
@Serializable
data class AvatarEvent(
    @SerialName("content") override val content: AvatarEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: AvatarEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.avatar"
) : StateEvent<AvatarEventContent> {

    @Serializable
    data class AvatarEventContent(
        @SerialName("url")
        val url: String,
        @SerialName("info")
        val info: ImageInfo? = null
    ) {
        @Serializable
        data class ImageInfo(
            @SerialName("h")
            val h: Int? = null,
            @SerialName("w")
            val w: Int? = null,
            @SerialName("mimetype")
            val mimeType: String? = null,
            @SerialName("size")
            val size: Int? = null,
            @SerialName("thumbnail_url")
            val thumbnailUrl: String? = null,
//                @JsonProperty("thumbnail_file") //TODO encryption
//                val thumbnailFile: EncryptedFile? = null,
            @SerialName("thumbnail_info")
            val thumbnailUnfo: ThumbnailInfo? = null
        ) {
            @Serializable
            data class ThumbnailInfo(
                @SerialName("h")
                val h: Int? = null,
                @SerialName("w")
                val w: Int? = null,
                @SerialName("mimetype")
                val mimeType: String? = null,
                @SerialName("size")
                val size: Int? = null
            )
        }
    }
}