package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomsKeyBackup

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3room_keyskeys">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys")
@HttpMethod(GET)
data class GetRoomsKeyBackup(
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, RoomsKeyBackup>