package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class RegisterResponse(
    @SerialName("user_id") val userId: UserId,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("access_token") val accessToken: String? = null
)