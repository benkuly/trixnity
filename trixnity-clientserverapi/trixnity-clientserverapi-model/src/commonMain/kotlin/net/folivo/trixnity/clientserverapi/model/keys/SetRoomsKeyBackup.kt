package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData
import net.folivo.trixnity.core.model.keys.RoomsKeyBackup

@Serializable
@Resource("/_matrix/client/v3/room_keys/keys")
data class SetRoomsKeyBackup<T : RoomKeyBackupSessionData>(
    @SerialName("version") val version: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<RoomsKeyBackup<T>, SetRoomKeysResponse>() {
    @Transient
    override val method = HttpMethod.Put
}