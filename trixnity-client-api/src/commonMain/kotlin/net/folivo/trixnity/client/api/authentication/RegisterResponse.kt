package net.folivo.trixnity.client.api.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class RegisterResponse(
    @SerialName("user_id") val userId: UserId,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)