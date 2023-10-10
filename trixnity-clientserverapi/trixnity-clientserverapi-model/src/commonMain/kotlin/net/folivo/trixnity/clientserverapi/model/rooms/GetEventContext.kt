package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3roomsroomidcontexteventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/context/{eventId}")
@HttpMethod(GET)
data class GetEventContext(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("filter") val filter: String? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetEventContext.Response> {
    @Serializable
    data class Response(
        @SerialName("start") val start: String? = null,
        @SerialName("end") val end: String? = null,
        @SerialName("event") val event: @Contextual RoomEvent<*>,
        @SerialName("events_before") val eventsBefore: List<@Contextual RoomEvent<*>>? = null,
        @SerialName("events_after") val eventsAfter: List<@Contextual RoomEvent<*>>? = null,
        @SerialName("state") val state: List<@Contextual StateEvent<*>>? = null
    )
}