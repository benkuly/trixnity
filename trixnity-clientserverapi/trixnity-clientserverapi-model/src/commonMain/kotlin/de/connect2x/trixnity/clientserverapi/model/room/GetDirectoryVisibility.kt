package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3directorylistroomroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/list/room/{roomId}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class GetDirectoryVisibility(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Unit, GetDirectoryVisibility.Response> {
    @Serializable
    data class Response(
        @SerialName("visibility") val visibility: DirectoryVisibility
    )
}