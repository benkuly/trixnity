package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/read_markers")
@HttpMethod(POST)
data class SetReadMarkers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetReadMarkers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("m.fully_read") val fullyRead: EventId,
        @SerialName("m.read") val read: EventId? = null,
    )
}