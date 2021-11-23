package net.folivo.trixnity.client.api.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangePasswordRequest(
    @SerialName("new_password")
    val newPassword: String,
    @SerialName("logout_devices")
    val logoutDevices: Boolean?
)