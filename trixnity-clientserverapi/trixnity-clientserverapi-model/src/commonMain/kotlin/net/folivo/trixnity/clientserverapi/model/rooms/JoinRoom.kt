package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
@Resource("/_matrix/client/v3/join/{roomIdOrRoomAliasId}")
data class JoinRoom(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("server_name") val serverNames: Set<String>? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<JoinRoom.Request, JoinRoom.Response>() {
    @Transient
    override val method = Post

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