package net.folivo.trixnity.client.api.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateDeviceRequest(
    @SerialName("display_name") val displayName: String
)
