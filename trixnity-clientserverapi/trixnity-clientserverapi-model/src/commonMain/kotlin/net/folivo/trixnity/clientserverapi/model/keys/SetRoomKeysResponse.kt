package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetRoomKeysResponse(
    @SerialName("count")
    val count: Long,
    @SerialName("etag")
    val etag: String
)