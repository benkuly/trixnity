package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#put_matrixclientv3directoryroomroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(PUT)
data class SetRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetRoomAlias.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("room_id") val roomId: RoomId,
    )
}