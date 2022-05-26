package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3room_keysversion">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/version")
@HttpMethod(POST)
data class SetRoomKeyBackupVersion(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response> {
    @Serializable
    data class Response(
        @SerialName("version")
        val version: String,
    )
}