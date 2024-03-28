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
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomidaliases">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/aliases")
@HttpMethod(GET)
@WithoutAuth
data class GetRoomAliases(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Unit, GetRoomAliases.Response> {
    @Serializable
    data class Response(
        @SerialName("aliases") val aliases: Set<RoomAliasId>
    )
}