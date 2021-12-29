package net.folivo.trixnity.client.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class GetJoinedMembersResponse(
    @SerialName("joined") val joined: Map<UserId, RoomMember>
) {
    @Serializable
    data class RoomMember(
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null
    )
}