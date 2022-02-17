package net.folivo.trixnity.clientserverapi.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteDevicesRequest(
    @SerialName("devices") val devices: List<String>,
)
