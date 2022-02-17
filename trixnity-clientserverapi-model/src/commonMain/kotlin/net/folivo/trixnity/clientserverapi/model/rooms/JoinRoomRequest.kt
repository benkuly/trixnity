package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
data class JoinRoomRequest(
    @SerialName("reason") val reason: String?,
    @SerialName("third_party_signed") val thirdPartySigned: Signed<ThirdParty, String>?,
) {
    @Serializable
    data class ThirdParty(
        @SerialName("sender") val sender: UserId,
        @SerialName("mxid") val mxid: UserId,
        @SerialName("token") val token: String,
    )
}