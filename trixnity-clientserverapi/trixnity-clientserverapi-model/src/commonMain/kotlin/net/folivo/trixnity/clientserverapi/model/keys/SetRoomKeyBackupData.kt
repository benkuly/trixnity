package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackupData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData

@Serializable
@Resource("/_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}")
data class SetRoomKeyBackupData<T : RoomKeyBackupSessionData>(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<RoomKeyBackupData<T>, SetRoomKeysResponse>() {
    @Transient
    override val method = Put
}