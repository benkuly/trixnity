package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3roomsroomidmessages">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/messages")
@HttpMethod(GET)
data class GetEvents(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("from") val from: String? = null,
    @SerialName("to") val to: String? = null,
    @SerialName("dir") val dir: Direction,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("filter") val filter: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetEvents.Response> {
    @Serializable
    enum class Direction {
        @SerialName("f")
        FORWARDS,

        @SerialName("b")
        BACKWARDS
    }

    @Serializable
    data class Response(
        @SerialName("start") val start: String,
        @SerialName("end") val end: String? = null,
        @SerialName("chunk") val chunk: List<@Contextual RoomEvent<*>>? = null,
        @SerialName("state") val state: List<@Contextual StateEvent<*>>? = null
    )
}