package net.folivo.trixnity.client.rest.api.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class WhoAmIResponse(
    @SerialName("user_id") val userId: UserId
)