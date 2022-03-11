package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackup
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData

@Serializable
@Resource("/_matrix/client/v3/room_keys/keys/{roomId}")
data class SetRoomKeyBackup<T : RoomKeyBackupSessionData>(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<RoomKeyBackup<T>, SetRoomKeysResponse>() {
    @Transient
    override val method = Put
}