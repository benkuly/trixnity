package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/joined_members")
data class GetJoinedMembers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetJoinedMembers.Response>() {
    @Transient
    override val method = Get

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