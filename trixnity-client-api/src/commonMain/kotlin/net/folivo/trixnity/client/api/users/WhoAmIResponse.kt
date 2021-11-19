package net.folivo.trixnity.client.api.users

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
internal data class WhoAmIResponse(
    @SerialName("user_id") val userId: UserId
)