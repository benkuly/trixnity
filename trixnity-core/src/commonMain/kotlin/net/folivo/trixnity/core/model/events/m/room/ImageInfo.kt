package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import net.folivo.trixnity.core.MSC2448

@Serializable
data class ImageInfo(
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
    val thumbnailInfo: ThumbnailInfo? = null,
    @MSC2448
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("blurhash", "xyz.amorgan.blurhash")
    @SerialName("xyz.amorgan.blurhash")
    val blurhash: String? = null,
) : FileBasedInfo
