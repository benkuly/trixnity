package net.folivo.trixnity.client.api.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteDevicesRequest(
    @SerialName("devices") val devices: List<String>,
)