package net.folivo.trixnity.client.rest.api.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @SerialName("auth") val auth: Auth,
    @SerialName("username") val username: String?,
    @SerialName("password") val password: String?,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("initial_device_display_name") val initialDeviceDisplayName: String?,
    @SerialName("inhibit_login") val inhibitLogin: Boolean?
) {
    @Serializable
    data class Auth(
        @SerialName("type") val type: String,
        @SerialName("session") val session: String?,
    )
}