package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class KickUserRequest(
    @SerialName("user_id") val userId: UserId,
    @SerialName("reason") val reason: String?
)