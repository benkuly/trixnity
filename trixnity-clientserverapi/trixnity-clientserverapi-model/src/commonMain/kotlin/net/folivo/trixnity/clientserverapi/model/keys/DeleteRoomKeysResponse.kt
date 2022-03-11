package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteRoomKeysResponse(
    @SerialName("count")
    val count: Int,
    @SerialName("etag")
    val etag: String
)