package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Resource("/_matrix/media/v3/thumbnail/{downloadUri}")
data class DownloadThumbnail(
    @SerialName("downloadUri") val downloadUri: String,
    @SerialName("width") val width: UInt,
    @SerialName("height") val height: UInt,
    @SerialName("method") val method: ThumbnailResizingMethod,
    @SerialName("allow_remote") val allowRemote: Boolean? = null
)