package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3directorylistroomroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/list/room/{roomId}")
@HttpMethod(GET)
@WithoutAuth
data class GetDirectoryVisibility(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Unit, GetDirectoryVisibility.Response> {
    @Serializable
    data class Response(
        @SerialName("visibility") val visibility: DirectoryVisibility
    )
}