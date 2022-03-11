package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.Membership

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/members")
data class GetMembers(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("at") val at: String? = null,
    @SerialName("membership") val membership: Membership? = null,
    @SerialName("not_membership") val notMembership: Membership? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetMembers.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("chunk") val chunk: Set<@Contextual Event.StateEvent<*>>
    )
}