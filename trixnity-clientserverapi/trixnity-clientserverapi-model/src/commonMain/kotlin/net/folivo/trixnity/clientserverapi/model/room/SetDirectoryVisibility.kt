package net.folivo.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3directorylistroomroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/list/room/{roomId}")
@HttpMethod(PUT)
data class SetDirectoryVisibility(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<SetDirectoryVisibility.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("visibility") val visibility: DirectoryVisibility,
    )
}