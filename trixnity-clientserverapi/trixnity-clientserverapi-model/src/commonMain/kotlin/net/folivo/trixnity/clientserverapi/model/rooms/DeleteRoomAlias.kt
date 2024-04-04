package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#delete_matrixclientv3directoryroomroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
@HttpMethod(DELETE)
data class DeleteRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Unit>