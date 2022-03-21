package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/joined_members")
@HttpMethod(GET)
data class GetJoinedMembers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetJoinedMembers.Response> {
    @Serializable
    data class Response(
        @SerialName("joined") val joined: Map<UserId, RoomMember>
    ) {
        @Serializable
        data class RoomMember(
            @SerialName("display_name") val displayName: String? = null,
            @SerialName("avatar_url") val avatarUrl: String? = null
        )
    }
}