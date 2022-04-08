package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/joined_rooms")
@HttpMethod(GET)
data class GetJoinedRooms(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetJoinedRooms.Response> {
    @Serializable
    data class Response(
        @SerialName("joined_rooms") val joinedRooms: Set<RoomId>
    )
}