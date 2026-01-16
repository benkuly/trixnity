package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomidjoined_members">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/joined_members")
@HttpMethod(GET)
data class GetJoinedMembers(
    @SerialName("roomId") val roomId: RoomId,
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