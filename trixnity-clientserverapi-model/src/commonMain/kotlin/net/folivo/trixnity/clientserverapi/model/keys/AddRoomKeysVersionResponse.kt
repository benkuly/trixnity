package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddRoomKeysVersionResponse(
    @SerialName("version")
    val version: String,
)
