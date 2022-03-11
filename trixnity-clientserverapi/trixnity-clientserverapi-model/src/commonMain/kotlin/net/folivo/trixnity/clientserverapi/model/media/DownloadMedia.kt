package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Resource("/_matrix/media/v3/download/{downloadUri}")
data class DownloadMedia(
    @SerialName("downloadUri") val downloadUri: String,
    @SerialName("allow_remote") val allowRemote: Boolean? = null,
)