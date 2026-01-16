package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.DELETE
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId

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