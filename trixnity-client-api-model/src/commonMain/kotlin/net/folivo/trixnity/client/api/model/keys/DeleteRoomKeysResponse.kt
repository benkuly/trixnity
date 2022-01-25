package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteRoomKeysResponse(
    @SerialName("count")
    val count: Int,
    @SerialName("etag")
    val etag: String
)