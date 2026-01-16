package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.keys.RoomsKeyBackup

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3room_keyskeys">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys")
@HttpMethod(PUT)
data class SetRoomsKeyBackup(
    @SerialName("version") val version: String,
) : MatrixEndpoint<RoomsKeyBackup, SetRoomKeysResponse>