package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class JoinRoomRequest(
    @SerialName("third_party_signed") val thirdPartySigned: ThirdPartySigned?,
) {
    @Serializable
    data class ThirdPartySigned(
        @SerialName("sender") val sender: UserId,
        @SerialName("mxid") val mxid: UserId,
        @SerialName("token") val token: String,
        @SerialName("signatures") val signatures: Map<String, Map<String, String>>
    )
}