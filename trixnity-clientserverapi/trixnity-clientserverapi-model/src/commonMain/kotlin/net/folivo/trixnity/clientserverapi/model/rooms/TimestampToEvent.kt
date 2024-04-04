package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv1roomsroomidtimestamp_to_event">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/rooms/{roomId}/timestamp_to_event")
@HttpMethod(GET)
data class TimestampToEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("ts") val timestamp: Long,
    @SerialName("dir") val dir: Direction,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, TimestampToEvent.Response> {

    @Serializable
    enum class Direction {
        @SerialName("f")
        FORWARDS,

        @SerialName("b")
        BACKWARDS
    }

    @Serializable
    data class Response(
        @SerialName("event_id") val eventId: EventId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
    )
}