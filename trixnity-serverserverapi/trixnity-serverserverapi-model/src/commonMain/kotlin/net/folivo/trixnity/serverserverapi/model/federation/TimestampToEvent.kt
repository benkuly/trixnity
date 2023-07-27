package net.folivo.trixnity.serverserverapi.model.federation

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
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#get_matrixfederationv1timestamp_to_eventroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/timestamp_to_event/{roomId}")
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