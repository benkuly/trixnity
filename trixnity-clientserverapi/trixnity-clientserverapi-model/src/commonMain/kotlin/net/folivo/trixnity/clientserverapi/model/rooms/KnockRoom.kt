package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/knock/{roomIdOrRoomAliasId}")
data class KnockRoom(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("server_name") val serverNames: Set<String>? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<KnockRoom.Request, KnockRoom.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("reason") val reason: String?,
    )

    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId
    )
}