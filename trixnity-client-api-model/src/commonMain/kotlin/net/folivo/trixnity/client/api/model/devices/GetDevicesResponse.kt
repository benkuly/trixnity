package net.folivo.trixnity.client.api.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetDevicesResponse(
    @SerialName("devices") val devices: List<Device>,
)

@Serializable
data class Device(
    @SerialName("device_id") val deviceId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("last_seen_ip") val lastSeenIp: String? = null,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
)
