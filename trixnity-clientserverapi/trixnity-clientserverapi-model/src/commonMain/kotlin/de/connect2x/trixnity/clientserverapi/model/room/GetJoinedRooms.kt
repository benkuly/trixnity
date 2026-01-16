package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3joined_rooms">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/joined_rooms")
@HttpMethod(GET)
data object GetJoinedRooms : MatrixEndpoint<Unit, GetJoinedRooms.Response> {
    @Serializable
    data class Response(
        @SerialName("joined_rooms") val joinedRooms: Set<RoomId>
    )
}