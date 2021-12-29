package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class BanUserRequest(
    @SerialName("user_id") val userId: UserId,
    @SerialName("reason") val reason: String?
)