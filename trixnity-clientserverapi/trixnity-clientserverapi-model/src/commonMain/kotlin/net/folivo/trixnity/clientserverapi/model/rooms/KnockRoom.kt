package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/knock/{roomIdOrRoomAliasId}")
@HttpMethod(POST)
data class KnockRoom(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("via") val via: Set<String>? = null,
    @Deprecated("use via instead", ReplaceWith("via"))
    @SerialName("server_name") val serverNames: Set<String>? = via,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<KnockRoom.Request, KnockRoom.Response> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String? = null,
    )

    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId
    )
}