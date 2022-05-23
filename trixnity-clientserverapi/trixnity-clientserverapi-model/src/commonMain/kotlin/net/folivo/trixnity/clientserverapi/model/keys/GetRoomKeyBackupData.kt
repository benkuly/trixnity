package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackupData

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}")
@HttpMethod(GET)
data class GetRoomKeyBackupData(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, RoomKeyBackupData>