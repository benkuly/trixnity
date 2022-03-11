package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/joined_rooms")
data class GetJoinedRooms(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetJoinedRooms.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("joined_rooms") val joinedRooms: Set<RoomId>
    )
}