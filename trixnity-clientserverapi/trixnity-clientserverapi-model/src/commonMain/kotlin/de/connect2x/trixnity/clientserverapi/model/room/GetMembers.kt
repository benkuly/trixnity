package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.Membership

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3roomsroomidmembers">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/members")
@HttpMethod(GET)
data class GetMembers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("at") val at: String? = null,
    @SerialName("membership") val membership: Membership? = null,
    @SerialName("not_membership") val notMembership: Membership? = null,
) : MatrixEndpoint<Unit, GetMembers.Response> {
    @Serializable
    data class Response(
        @SerialName("chunk") val chunk: Set<@Contextual StateEvent<*>>
    )
}