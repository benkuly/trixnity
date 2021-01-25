package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-avatar">matrix spec</a>
 */
@Serializable
data class AvatarEventContent(
    @SerialName("url")
    val url: String,
    @SerialName("info")
    val info: ImageInfo? = null
) : StateEventContent {
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