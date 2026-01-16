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
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomidaliases">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/aliases")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class GetRoomAliases(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Unit, GetRoomAliases.Response> {
    @Serializable
    data class Response(
        @SerialName("aliases") val aliases: Set<RoomAliasId>
    )
}