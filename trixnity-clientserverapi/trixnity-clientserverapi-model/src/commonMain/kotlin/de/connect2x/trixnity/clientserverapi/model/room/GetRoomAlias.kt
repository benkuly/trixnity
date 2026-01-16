package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3directoryroomroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class GetRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
) : MatrixEndpoint<Unit, GetRoomAlias.Response> {
    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("servers") val servers: List<String>
    )
}