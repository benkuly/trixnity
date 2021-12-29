package net.folivo.trixnity.client.api.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("type")
    val type: String,
    @SerialName("identifier")
    val identifier: IdentifierType,
    @SerialName("password")
    val password: String? = null,
    @SerialName("token")
    val token: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    @SerialName("initial_device_display_name")
    val initialDeviceDisplayName: String? = null
)