package net.folivo.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3roomsroomidunban">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/unban")
@HttpMethod(POST)
data class UnbanUser(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<UnbanUser.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("user_id") val userId: UserId,
        @SerialName("reason") val reason: String? = null,
    )
}