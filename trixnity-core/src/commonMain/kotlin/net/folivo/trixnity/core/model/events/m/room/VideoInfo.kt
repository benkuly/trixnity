package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoInfo(
    @SerialName("duration")
    val duration: Long? = null,
    @SerialName("h")
    val height: Int? = null,
    @SerialName("w")
    val width: Int? = null,
    @SerialName("mimetype")
    override val mimeType: String? = null,
    @SerialName("size")
    override val size: Long? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("thumbnail_file")
    val thumbnailFile: EncryptedFile? = null,
    @SerialName("thumbnail_info")
    val thumbnailInfo: ThumbnailInfo? = null
) : FileBasedInfo