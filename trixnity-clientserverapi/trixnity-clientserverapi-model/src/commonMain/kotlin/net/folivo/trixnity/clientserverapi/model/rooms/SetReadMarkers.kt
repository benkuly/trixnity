package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/read_markers")
data class SetReadMarkers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetReadMarkers.Request, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("m.fully_read") val fullyRead: EventId,
        @SerialName("m.read") val read: EventId? = null,
    )
}