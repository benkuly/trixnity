package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    @SerialName("mimetype")
    val mimeType: String? = null,
    @SerialName("size")
    val size: Int? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("thumbnail_file")
    val thumbnailFile: EncryptedFile? = null,
    @SerialName("thumbnail_info")
    val thumbnailInfo: ThumbnailInfo? = null
)