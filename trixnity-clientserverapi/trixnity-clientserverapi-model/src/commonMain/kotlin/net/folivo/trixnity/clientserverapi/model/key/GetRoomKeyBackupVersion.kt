package net.folivo.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3room_keysversion">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/version")
@HttpMethod(GET)
data object GetRoomKeyBackupVersion : MatrixEndpoint<Unit, GetRoomKeysBackupVersionResponse>