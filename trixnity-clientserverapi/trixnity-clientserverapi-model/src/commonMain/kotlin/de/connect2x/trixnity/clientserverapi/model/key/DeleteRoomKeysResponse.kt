package de.connect2x.trixnity.clientserverapi.model.key

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteRoomKeysResponse(
    @SerialName("count")
    val count: Long,
    @SerialName("etag")
    val etag: String
)