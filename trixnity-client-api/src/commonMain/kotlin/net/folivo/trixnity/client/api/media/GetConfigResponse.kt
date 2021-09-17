package net.folivo.trixnity.client.api.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetConfigResponse(
    @SerialName("m.upload.size") val maxUploadSize: Int
)