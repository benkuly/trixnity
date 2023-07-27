package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#delete_matrixclientv3room_keyskeys">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys")
@HttpMethod(DELETE)
data class DeleteRoomsKeyBackup(
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, DeleteRoomKeysResponse>