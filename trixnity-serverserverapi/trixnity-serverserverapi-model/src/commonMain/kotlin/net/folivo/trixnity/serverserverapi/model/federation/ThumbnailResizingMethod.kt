package net.folivo.trixnity.serverserverapi.model.federation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThumbnailResizingMethod {
    @SerialName("crop")
    CROP,

    @SerialName("scale")
    SCALE
}