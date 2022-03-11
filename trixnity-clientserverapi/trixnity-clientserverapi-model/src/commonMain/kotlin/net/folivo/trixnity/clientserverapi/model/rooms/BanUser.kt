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
@Resource("/_matrix/client/v3/rooms/{roomId}/ban")
data class BanUser(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<BanUser.Request, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("user_id") val userId: UserId,
        @SerialName("reason") val reason: String?
    )
}