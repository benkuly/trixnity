package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.DELETE
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#delete_matrixclientv3directoryroomroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(DELETE)
data class DeleteRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
) : MatrixEndpoint<Unit, Unit>