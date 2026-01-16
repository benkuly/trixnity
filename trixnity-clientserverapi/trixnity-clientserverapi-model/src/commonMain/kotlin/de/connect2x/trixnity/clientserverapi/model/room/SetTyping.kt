package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3roomsroomidtypinguserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/typing/{userId}")
@HttpMethod(PUT)
data class SetTyping(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<SetTyping.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("typing") val typing: Boolean,
        @SerialName("timeout") val timeout: Long? = null,
    )
}