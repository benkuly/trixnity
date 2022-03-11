package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}")
data class DeleteRoomKeyBackupData(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, DeleteRoomKeysResponse>() {
    @Transient
    override val method = Delete
}