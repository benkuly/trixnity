package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KnockRoomRequest(
    @SerialName("reason") val reason: String?,
)