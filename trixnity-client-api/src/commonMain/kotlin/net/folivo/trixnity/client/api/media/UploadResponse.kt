package net.folivo.trixnity.client.api.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(
    @SerialName("content_uri") val contentUri: String,
)