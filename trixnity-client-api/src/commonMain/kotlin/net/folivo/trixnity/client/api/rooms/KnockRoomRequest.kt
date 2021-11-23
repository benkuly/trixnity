package net.folivo.trixnity.client.api.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KnockRoomRequest(
    @SerialName("reason") val reason: String?,
)