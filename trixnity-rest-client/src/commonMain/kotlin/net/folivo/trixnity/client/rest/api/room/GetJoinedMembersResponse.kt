package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId

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