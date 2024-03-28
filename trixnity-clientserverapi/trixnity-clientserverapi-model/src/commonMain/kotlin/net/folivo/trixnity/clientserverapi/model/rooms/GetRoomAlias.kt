package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3directoryroomroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(GET)
@WithoutAuth
data class GetRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
) : MatrixEndpoint<Unit, GetRoomAlias.Response> {
    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("servers") val servers: List<String>
    )
}