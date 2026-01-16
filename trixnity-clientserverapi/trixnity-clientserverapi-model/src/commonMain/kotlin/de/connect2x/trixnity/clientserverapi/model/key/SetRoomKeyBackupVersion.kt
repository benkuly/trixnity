package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3room_keysversion">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/version")
@HttpMethod(POST)
data object SetRoomKeyBackupVersion : MatrixEndpoint<SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response> {
    @Serializable
    data class Response(
        @SerialName("version")
        val version: String,
    )
}