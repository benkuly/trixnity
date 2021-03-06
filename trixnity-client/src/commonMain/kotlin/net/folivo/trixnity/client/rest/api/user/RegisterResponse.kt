package net.folivo.trixnity.client.rest.api.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class RegisterResponse(
    @SerialName("user_id") val userId: UserId,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)