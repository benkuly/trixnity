package net.folivo.trixnity.client.api.model.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class WhoAmIResponse(
    @SerialName("user_id") val userId: UserId,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("is_guest") val isGuest: Boolean?
)