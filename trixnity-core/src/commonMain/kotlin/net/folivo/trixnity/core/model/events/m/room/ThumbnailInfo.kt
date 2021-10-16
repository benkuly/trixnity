package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailInfo(
    @SerialName("w")
    val width: Int? = null,
    @SerialName("h")
    val height: Int? = null,
    @SerialName("mimetype")
    val mimeType: String? = null,
    @SerialName("size")
    val size: Int? = null
)