package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/join/{roomIdOrRoomAliasId}")
@HttpMethod(POST)
data class JoinRoom(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("via") val via: Set<String>? = null,
    @Deprecated("use via instead", ReplaceWith("via"))
    @SerialName("server_name") val serverNames: Set<String>? = via,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<JoinRoom.Request, JoinRoom.Response> {
    @Serializable
    data class Request(
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

    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId
    )
}