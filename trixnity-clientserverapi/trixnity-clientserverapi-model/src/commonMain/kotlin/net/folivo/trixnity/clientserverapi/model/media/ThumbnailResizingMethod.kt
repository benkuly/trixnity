package net.folivo.trixnity.clientserverapi.model.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThumbnailResizingMethod {
    @SerialName("crop")
    CROP,

    @SerialName("scale")
    SCALE
}