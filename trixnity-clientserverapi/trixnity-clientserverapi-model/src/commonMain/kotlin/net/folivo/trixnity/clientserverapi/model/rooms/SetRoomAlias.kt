package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/directory/room/{roomAliasId}")
data class SetRoomAlias(
    @SerialName("roomAliasId") val roomAliasId: RoomAliasId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetRoomAlias.Request, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("room_id") val roomId: RoomId,
    )
}