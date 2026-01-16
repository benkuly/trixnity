package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidinvite">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/invite")
@HttpMethod(POST)
data class InviteUser(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<InviteUser.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("user_id") val userId: UserId,
        @SerialName("reason") val reason: String? = null,
    )
}