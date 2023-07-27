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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.Membership

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3roomsroomidmembers">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/members")
@HttpMethod(GET)
data class GetMembers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("at") val at: String? = null,
    @SerialName("membership") val membership: Membership? = null,
    @SerialName("not_membership") val notMembership: Membership? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetMembers.Response> {
    @Serializable
    data class Response(
        @SerialName("chunk") val chunk: Set<@Contextual Event.StateEvent<*>>
    )
}