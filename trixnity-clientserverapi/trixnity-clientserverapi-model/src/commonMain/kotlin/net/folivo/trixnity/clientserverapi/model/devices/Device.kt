package net.folivo.trixnity.clientserverapi.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    @SerialName("device_id") val deviceId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("last_seen_ip") val lastSeenIp: String? = null,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
)