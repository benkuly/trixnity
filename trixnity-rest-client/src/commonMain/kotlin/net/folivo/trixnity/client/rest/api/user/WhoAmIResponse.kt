package net.folivo.trixnity.appservice.rest.api.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
internal data class WhoAmIResponse(
    @SerialName("user_id") val userId: UserId
)