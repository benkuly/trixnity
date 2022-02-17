package net.folivo.trixnity.clientserverapi.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateDeviceRequest(
    @SerialName("display_name") val displayName: String
)
