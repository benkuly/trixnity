package net.folivo.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#delete_matrixclientv3room_keyskeysroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys/{roomId}")
@HttpMethod(DELETE)
data class DeleteRoomKeyBackup(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("version") val version: String,
) : MatrixEndpoint<Unit, DeleteRoomKeysResponse>