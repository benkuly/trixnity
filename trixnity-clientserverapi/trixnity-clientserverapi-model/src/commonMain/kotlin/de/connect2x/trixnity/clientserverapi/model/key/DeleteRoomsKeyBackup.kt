package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.DELETE
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#delete_matrixclientv3room_keyskeys">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys")
@HttpMethod(DELETE)
data class DeleteRoomsKeyBackup(
    @SerialName("version") val version: String,
) : MatrixEndpoint<Unit, DeleteRoomKeysResponse>