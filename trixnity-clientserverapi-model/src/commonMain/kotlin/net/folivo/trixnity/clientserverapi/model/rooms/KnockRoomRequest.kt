package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KnockRoomRequest(
    @SerialName("reason") val reason: String?,
)