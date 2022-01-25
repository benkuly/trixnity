package net.folivo.trixnity.client.api.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @SerialName("username") val username: String?,
    @SerialName("password") val password: String?,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("initial_device_display_name") val initialDeviceDisplayName: String?,
    @SerialName("inhibit_login") val inhibitLogin: Boolean?,
    @SerialName("type") val type: String? = null
)